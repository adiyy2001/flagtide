import re
import sys

C1 = 0xCC9E2D51
C2 = 0x1B873593
MASK = 0xFFFFFFFF
BUCKET_SPACE = 100000


def rotl(value, shift):
    return ((value << shift) | (value >> (32 - shift))) & MASK


def murmur3_x86_32(data, seed):
    h = seed & MASK
    block_count = len(data) // 4
    for index in range(block_count):
        k = int.from_bytes(data[index * 4:index * 4 + 4], "little")
        k = (k * C1) & MASK
        k = rotl(k, 15)
        k = (k * C2) & MASK
        h ^= k
        h = rotl(h, 13)
        h = (h * 5 + 0xE6546B64) & MASK
    tail = data[block_count * 4:]
    if tail:
        k = int.from_bytes(tail, "little")
        k = (k * C1) & MASK
        k = rotl(k, 15)
        k = (k * C2) & MASK
        h ^= k
    h ^= len(data)
    h ^= h >> 16
    h = (h * 0x85EBCA6B) & MASK
    h ^= h >> 13
    h = (h * 0xC2B2AE35) & MASK
    h ^= h >> 16
    return h


def utf16_units(text):
    raw = text.encode("utf-16-le", "surrogatepass")
    return [int.from_bytes(raw[i:i + 2], "little") for i in range(0, len(raw), 2)]


def utf8_with_replacement(text):
    units = utf16_units(text)
    out = bytearray()
    index = 0
    while index < len(units):
        unit = units[index]
        index += 1
        if 0xD800 <= unit <= 0xDBFF:
            if index < len(units) and 0xDC00 <= units[index] <= 0xDFFF:
                low = units[index]
                index += 1
                code_point = 0x10000 + ((unit - 0xD800) << 10) + (low - 0xDC00)
            else:
                code_point = 0xFFFD
        elif 0xDC00 <= unit <= 0xDFFF:
            code_point = 0xFFFD
        else:
            code_point = unit
        out.extend(chr(code_point).encode("utf-8"))
    return bytes(out)


def hash_text(text, seed=0):
    return murmur3_x86_32(utf8_with_replacement(text), seed)


def bucket_input(flag_key, salt, context_key):
    return flag_key + "." + salt + "." + context_key


def bucket(flag_key, salt, context_key):
    return hash_text(bucket_input(flag_key, salt, context_key)) % BUCKET_SPACE


def smhasher_verification():
    collected = bytearray()
    for length in range(256):
        key = bytes(range(length))
        collected.extend(murmur3_x86_32(key, 256 - length).to_bytes(4, "little"))
    return murmur3_x86_32(bytes(collected), 0)


PUBLISHED = [
    (b"", 0, 0x00000000),
    (b"", 1, 0x514E28B7),
    (b"", 0xFFFFFFFF, 0x81F16F39),
    (b"\xff\xff\xff\xff", 0, 0x76293B50),
    (b"\x21\x43\x65\x87", 0, 0xF55B516B),
    (b"\x21\x43\x65\x87", 0x5082EDEE, 0x2362F9DE),
    (b"\x21\x43\x65", 0, 0x7E4A8634),
    (b"\x21\x43", 0, 0xA0F7B07A),
    (b"\x21", 0, 0x72661CF4),
    (b"\x00\x00\x00\x00", 0, 0x2362F9DE),
    (b"\x00\x00\x00", 0, 0x85F0B427),
    (b"\x00\x00", 0, 0x30F4C306),
    (b"\x00", 0, 0x514E28B7),
    (b"test", 0, 0xBA6BD213),
    (b"Hello, world!", 0, 0xC0363E43),
    (b"The quick brown fox jumps over the lazy dog", 0, 0x2E4FF723),
    (b"Hello, world!", 0x9747B28C, 0x24884CBA),
    (b"The quick brown fox jumps over the lazy dog", 0x9747B28C, 0x2FA826CD),
    (b"aaaa", 0x9747B28C, 0x5A97808A),
    (b"aaa", 0x9747B28C, 0x283E0130),
    (b"aa", 0x9747B28C, 0x5D211726),
    (b"a", 0x9747B28C, 0x7FA09EA6),
    (b"abcd", 0x9747B28C, 0xF0478627),
    (b"abc", 0x9747B28C, 0xC84A62DD),
    (b"ab", 0x9747B28C, 0x74875592),
]

SMHASHER_VERIFICATION_VALUE = 0xB0F57EE3

SEMVER_PATTERN = re.compile(
    r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
    r"(?:-((?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?"
    r"(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
)


def parse_semver(text):
    match = SEMVER_PATTERN.fullmatch(text)
    if match is None:
        return None
    major, minor, patch, pre, build = match.groups()
    return (int(major), int(minor), int(patch), pre.split(".") if pre else [], build.split(".") if build else [])


def compare_identifiers(left, right):
    left_numeric = left.isdigit()
    right_numeric = right.isdigit()
    if left_numeric and right_numeric:
        return (int(left) > int(right)) - (int(left) < int(right))
    if left_numeric:
        return -1
    if right_numeric:
        return 1
    return (left > right) - (left < right)


def compare_semver(left, right):
    for index in range(3):
        if left[index] != right[index]:
            return -1 if left[index] < right[index] else 1
    left_pre, right_pre = left[3], right[3]
    if not left_pre and not right_pre:
        return 0
    if not left_pre:
        return 1
    if not right_pre:
        return -1
    for left_id, right_id in zip(left_pre, right_pre):
        result = compare_identifiers(left_id, right_id)
        if result != 0:
            return result
    return (len(left_pre) > len(right_pre)) - (len(left_pre) < len(right_pre))


def compare_semver_text(left, right):
    parsed_left = parse_semver(left)
    parsed_right = parse_semver(right)
    if parsed_left is None or parsed_right is None:
        raise ValueError("invalid semver: " + repr((left, right)))
    return compare_semver(parsed_left, parsed_right)


def self_test():
    for data, seed, expected in PUBLISHED:
        actual = murmur3_x86_32(data, seed)
        assert actual == expected, (data, seed, hex(actual), hex(expected))
    assert smhasher_verification() == SMHASHER_VERIFICATION_VALUE, hex(smhasher_verification())
    assert utf8_with_replacement("\ud800") == b"\xef\xbf\xbd"
    assert utf8_with_replacement("😀") == "\U0001f600".encode("utf-8")
    assert utf8_with_replacement("\ude00\ud83d") == b"\xef\xbf\xbd\xef\xbf\xbd"
    return len(PUBLISHED) + 1


if __name__ == "__main__":
    checked = self_test()
    sys.stdout.write("oracle self test passed (" + str(checked) + " reference values)\n")
