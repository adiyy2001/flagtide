package dev.flagwire.domain.value;

public record ProjectKey(String value) implements Comparable<ProjectKey> {

  public ProjectKey {
    Slugs.require("project", value);
  }

  public static ProjectKey of(String value) {
    return new ProjectKey(value);
  }

  @Override
  public int compareTo(ProjectKey other) {
    return this.value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return this.value;
  }
}
