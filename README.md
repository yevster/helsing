# Helsing

* [Contributing](#contributing)
* [Legal](#legal)
* [Reporting Vulnerabilities](#reporting-vulnerabilities)
* [Projects](#projects)

Provides utilities for finding unused code within a self-complete application code base

## Contributing

Information for how to contribute can be found in [the contribution guidelines](./docs/CONTRIBUTING.md)

## Use

Helsing is currently in initial (alpha) development. In current provides a single command, `dead-class-candidates`, which analyzes `*.class` and `*.java` files within an application and determines which, if any, are not currently referenced within the available source

To use, get the standalone jar from the latest alpha tag, and run

```
java -jar <jar name> dead-class-candidates --directory <project directory>
```

on a built project (built meaning the project directory contains compiled `*.class` files). Additionally, the `--trace` argument may be given a full-qualified class name, which will log additional information about the structure discovered for that class (if a use of another class isn't being detected within it), an the discovered uses of that class

Two other arguments are supported: `--external` and `--exclude`. These take one or more class/package patterns (`org.company.Class`, `org.company.*`). The external argument still evaluates matching class's use of other classes, but does not consider those classes as "dead" if they are not referenced. The exclude argument ignores matching classes during analysis entirely.
## Legal

This project is distributed under the [MIT License](https://opensource.org/licenses/MIT). There are no requirements for using it in your own project (a line in a NOTICES file is appreciated but not necessary for use)

The requirement for a copy of the license being included in distributions is fulfilled by a copy of the [LICENSE](./LICENSE) file being included in constructed JAR archives

## Reporting Vulnerabilities

If you discover a security vulnerability, contact the development team by e-mail at `vulnerabilities@starchartlabs.org`

## Projects

TODO Describe any sub-projects
