# How to Contribute to GraalVM Documentation

The GraalVM documentation is open source and anyone can contribute to make it perfect and comprehensive.
Before contributing, please read the main [contribution guide](../CONTRIBUTING.md) and, if you use AI assistance, the [Coding Assistants policy](../CODING_ASSISTANTS.md).

The GraalVM documentation is presented in the form of:
* reference documentation which includes getting started and user guides, reference manuals, examples, security guidelines
* Javadoc APIs documentation for application developers, or those who write Java compatibility tests or seek to re-implement the GraalVM platform

Here you will find most of the GraalVM documentation sources, in the same hierarchy as displayed on the [GraalVM website](https://www.graalvm.org/docs/introduction/).
The Truffle framework documentation can be found in the [graal/truffle/docs](https://github.com/oracle/graal/tree/master/truffle/docs) folder.
GraalVM language implementations are developed and tested in repositories separate from the GraalVM core repository, so their user documentation can be found in:

* [GraalJS](https://github.com/oracle/graaljs/tree/master/docs/user) - JavaScript and Node.js
* [GraalPy](https://github.com/oracle/graalpython/tree/master/docs/user) - Python
* [TruffleRuby](https://github.com/oracle/truffleruby/tree/master/doc/user) - Ruby

To update the documentation:

1. Create a GitHub account or sign in to your existing account
2. Navigate to the source file you intend to update
3. Edit the files locally or use GitHub's web editor for small changes.
   > Note: GitHub's online editor can be opened by pressing `.` on the repository page. For example, on [https://github.com/oracle/graal](https://github.com/oracle/graal), pressing `.` opens [https://github.dev/oracle/graal](https://github.dev/oracle/graal).
4. Create a Pull Request (PR) with a clear summary and testing information
5. Sign the [Oracle Contributor Agreement](https://oca.opensource.oracle.com/)
6. Watch your PR for review and pipeline results

A member from the GraalVM project team will review your PR and merge as appropriate.
There is a CI pipeline which will pick up your change once merged to the master branch, and publish on the website.

### Code of Conduct

The GraalVM core and its projects are hosted in the Oracle organization on GitHub, so we expect contributors to abide by the [Contributor Covenant Code of Conduct](https://www.graalvm.org/community/conduct/).
For more specific guidelines see [Contribute to GraalVM](https://www.graalvm.org/community/contributors/).

Do not hesitate to push fixes to broken links or typos, update an outdated section, propose information if you find it missing, or propose a new feature documentation.
Help us make GraalVM better by improving the existing and contributing new documentation!
