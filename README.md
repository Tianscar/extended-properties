# Extended Properties
Lightweight, dependency-free expansion working around `java.util.Properties`, mainly for ini support.

Requires Java 8 or later.

## Add the library to your project (gradle)
1. Add the Maven Central repository (if not exist) to your build file:
```groovy
repositories {
    ...
    mavenCentral()
}
```

2. Add the dependency:
```groovy
dependencies {
    ...
    implementation 'com.tianscar.properties:extended-properties:1.0.0'
}
```

## Usage
[JavaDoc](https://docs.tianscar.com/extended-properties)  
[Examples](/src/test/java/com/tianscar/properties/test)

## License
[Apache-2.0](/LICENSE) (c) Karstian Lee

### This project currently uses some code from the following projects:
Apache-2.0 [Apache Harmony](https://harmony.apache.org)  
Public Domain [JSR 173 Streaming API For XML Reference Implementation](https://mvnrepository.com/artifact/com.bea.xml/jsr173-ri/)
