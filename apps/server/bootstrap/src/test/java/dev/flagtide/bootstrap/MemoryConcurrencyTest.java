package dev.flagtide.bootstrap;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(MemoryProfile.class)
class MemoryConcurrencyTest extends ConcurrencyTest {}
