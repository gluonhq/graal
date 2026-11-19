# Coding Assistants

GraalVM contributors may use AI coding assistants and similar tools when preparing contributions. We expect responsible use of such tools to help increase development velocity and, in many cases, improve contribution quality, for example by helping contributors produce clearer documentation, broader test coverage, and more complete supporting changes. This document is informed by the Linux kernel's [AI Coding Assistants](https://github.com/torvalds/linux/blob/master/Documentation/process/coding-assistants.rst) policy and adapts that general approach for the GraalVM project.

## Scope

For purposes of this document, "coding assistants" includes AI-based tools that help draft, transform, explain, review, or summarize code, tests, documentation, or commit text.
This policy applies to contributions and project interactions prepared with such tools, including pull requests and issues filed with the project.

## General Expectations

Use of a coding assistant does not change the standard contribution process. Contributors using AI assistance are expected to follow [CONTRIBUTING.md](CONTRIBUTING.md), the relevant subproject documentation, and all review, testing, licensing, and code ownership requirements that apply to any other contribution.

## Contributor Responsibility

The human contributor submitting a change remains responsible for the entire contribution, including any AI-assisted portion. In particular, the contributor is expected to:

- review and understand the submitted code, tests, documentation, and commit messages
- verify that the change is correct, necessary, and consistent with project standards
- address reviewer questions and follow-up changes without deferring responsibility to a tool
- stand behind the contribution during review and maintenance

If a contributor cannot explain, defend, or maintain an AI-assisted change, the contribution may be rejected.

## Attribution

Disclosure of AI assistance is encouraged when it helps reviewers understand how a change was produced, but explicit attribution to a specific model or tool is optional. Contributors may mention AI assistance in a pull request description, commit message body, or other review context when they believe it is useful. GraalVM does not require a dedicated attribution tag or a model name.

## Oracle Contributor Agreement

The [Oracle Contributor Agreement](https://oca.opensource.oracle.com/) and the project's normal contribution requirements apply to contributions submitted for inclusion in the project, whether AI-assisted or not. Contributors must ensure that they have the right to submit the material, that the submission complies with the project's licensing and legal requirements, and that no coding assistant terms or generated-content restrictions conflict with those obligations.

## Maintainer Review

Use of AI assistance does not create any presumption that a change is correct, review-ready, or exempt from normal scrutiny. Maintainers may request clarification about provenance, design intent, licensing, testing, or contributor understanding for any contribution, including an AI-assisted one.
