val deps: Map<String, String> by extra

dependencies {
    api("org.apache.flink", "flink-streaming-java_2.12", deps["flink"])

    testImplementation("org.apache.flink", "flink-test-utils_2.12", deps["flink"])
}
