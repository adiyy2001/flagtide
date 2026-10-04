package dev.flagtide.bootstrap;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(StreamProfiles.Memory.class)
class MemoryStreamTest extends StreamTest {}
