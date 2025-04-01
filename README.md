# IPMI Java Client
![GitHub release (with filter)](https://img.shields.io/github/v/release/metricshub/ipmi-java)
![Build](https://img.shields.io/github/actions/workflow/status/metricshub/ipmi-java/deploy.yml)
![GitHub top language](https://img.shields.io/github/languages/top/metricshub/ipmi-java)
![License](https://img.shields.io/github/license/metricshub/ipmi-java)

This project is a fork of the excellent [IPMI Library for Java by Verax Systems](https://veraxsystems.com/ipmi-library-for-java/) ([see also](https://en.wikipedia.org/wiki/Verax_IPMI)). It is however not related to [another fork by rbuckland](https://github.com/rbuckland/ipmilib).

See **[Project Documentation](https://metricshub.org/ipmi-java)** and the [Javadoc](https://metricshub.org/ipmi-java/apidocs) for more information on how to use this library in your code.

The IPMI Java Client is a library that communicates with the IPMI host, fetches Field Replaceable Units (FRUs) and Sensors information then reports these information as a text output.

## Build instructions

This is a simple Maven project. Build with:

```bash
mvn verify
```

## Release instructions

The artifact is deployed to Sonatype's [Maven Central](https://central.sonatype.com/).

The actual repository URL is https://s01.oss.sonatype.org/, with server Id `ossrh` and requires credentials to deploy
artifacts manually.

But it is strongly recommended to only use [GitHub Actions "Release to Maven Central"](actions/workflows/release.yml) to perform a release:

* Manually trigger the "Release" workflow
* Specify the version being released and the next version number (SNAPSHOT)
* Release the corresponding staging repository on [Sonatype's Nexus server](https://s01.oss.sonatype.org/)
* Merge the PR that has been created to prepare the next version

## License

License is GNU General Lesser Public License (LGPL) version 3.0. Each source file includes the LGPL-3 header (build will fail otherwise).
To update source files with the proper header, simply execute the below command:

```bash
mvn license:update-file-header
```
