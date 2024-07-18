## [0.7.2](https://github.com/PharmGKB/pgkb-common/compare/v0.7.1...v0.7.2) (2024-07-18)

### Bug Fixes

* fix transitivity bug in HaplotypeNameComparator ([446f4c1](https://github.com/PharmGKB/pgkb-common/commit/446f4c126e038904a0355e072600d0a176485779))

## [0.7.1](https://github.com/PharmGKB/pgkb-common/compare/v0.7.0...v0.7.1) (2024-06-14)

### Bug Fixes

* add convenience methods to ComparisonChain for haplotypes and chromosomes ([f6ab4c9](https://github.com/PharmGKB/pgkb-common/commit/f6ab4c94e87293ddab55d39bc70176d537ce9a91))
* **api:** sort allele list in GSI options response ([33fa0bf](https://github.com/PharmGKB/pgkb-common/commit/33fa0bfcbc79c993702f06cc1f53e7f1efac7c4e))

## [0.7.0](https://github.com/PharmGKB/pgkb-common/compare/v0.6.0...v0.7.0) (2023-10-23)


### Features

* support md5 hashing ([7d7f662](https://github.com/PharmGKB/pgkb-common/commit/7d7f66229ec4a39f175d7ea077ebf3c205103f49))


### Bug Fixes

* fix generics ([4a6f483](https://github.com/PharmGKB/pgkb-common/commit/4a6f483ef16823fc9f411dc3873279378e1e96fe))
* improve error message ([8477e63](https://github.com/PharmGKB/pgkb-common/commit/8477e63fbfb864ba53005cec0cf9229dd5cf1ae1))
* return absolute path if provided path has no parent ([0e035fe](https://github.com/PharmGKB/pgkb-common/commit/0e035fe54e9547221d7ebd696fd7455381a8f5cc))

## [0.6.0](https://github.com/PharmGKB/pgkb-common/compare/v0.5.8...v0.6.0) (2023-01-25)


### Features

* add support for ANSI console codes ([6e21e16](https://github.com/PharmGKB/pgkb-common/commit/6e21e1663fca95a5cca7e6d85152aa802a9f68b9))

## [0.5.8](https://github.com/PharmGKB/pgkb-common/compare/v0.5.7...v0.5.8) (2023-01-17)


### Bug Fixes

* add nullability constraints to ComparisonChain ([74d7e20](https://github.com/PharmGKB/pgkb-common/commit/74d7e2096d048528cfe98952abd1f995c313a5b8))

## [0.5.7](https://github.com/PharmGKB/pgkb-common/compare/v0.5.6...v0.5.7) (2023-01-16)


### Bug Fixes

* add ComparisonChain ([ee25ad8](https://github.com/PharmGKB/pgkb-common/commit/ee25ad81f451f42ca081273e948b5527883662e6))

## [0.5.6](https://github.com/PharmGKB/pgkb-common/compare/v0.5.5...v0.5.6) (2023-01-16)


### Bug Fixes

* fix generics in ComparatorUtils.compareCollectionOfMaps ([5b63e15](https://github.com/PharmGKB/pgkb-common/commit/5b63e15130395d5d2c018b72337c9ab57dd7eaca))

## [0.5.5](https://github.com/PharmGKB/pgkb-common/compare/v0.5.4...v0.5.5) (2023-01-16)


### Bug Fixes

* add ComparatorUtils.compareCollectionOfMaps ([e62e2bc](https://github.com/PharmGKB/pgkb-common/commit/e62e2bc1f2f65c486db7da7944c08898235abc1e))

## [0.5.4](https://github.com/PharmGKB/pgkb-common/compare/v0.5.3...v0.5.4) (2023-01-15)


### Bug Fixes

* add ComparatorUtils.compareMap ([78d3db4](https://github.com/PharmGKB/pgkb-common/commit/78d3db44fcd0d594d66aa824a9a0f4f5a5b32fb6))

## [0.5.3](https://github.com/PharmGKB/pgkb-common/compare/v0.5.2...v0.5.3) (2023-01-15)


### Bug Fixes

* move ComparatorUtils to pgkb-common ([e0769d9](https://github.com/PharmGKB/pgkb-common/commit/e0769d9fa2217a5fd74edb5b44370b21ad337b9d))

## [0.5.2](https://github.com/PharmGKB/pgkb-common/compare/v0.5.1...v0.5.2) (2022-11-15)


### ⚠ BREAKING CHANGES

* update dependencies

### Bug Fixes

* add TimeUtils.humanReadablePreciseDuration ([50e2065](https://github.com/PharmGKB/pgkb-common/commit/50e20659b4bf93d0338ddae4e84e69c970bf6ff5))


### Miscellaneous Chores

* update dependencies ([d977c37](https://github.com/PharmGKB/pgkb-common/commit/d977c37b7256db55b1a1a3c1e5477f82b77a11c8))

## [0.5.1](https://github.com/PharmGKB/pgkb-common/compare/v0.5.0...v0.5.1) (2022-05-27)


### ⚠ BREAKING CHANGES

* upgrade to yarn 3.2.1 and update dependencies

### Bug Fixes

* throw InvalidPathException when path is invalid ([fd1e790](https://github.com/PharmGKB/pgkb-common/commit/fd1e7908eb170f704af0193a9e7fe2ec160ce51d))


### Build System

* upgrade to yarn 3.2.1 and update dependencies ([d903a7b](https://github.com/PharmGKB/pgkb-common/commit/d903a7b2548f46463518db7f32d1ba0ab430d6ee))

## [0.5.0](https://github.com/PharmGKB/pgkb-common/compare/v0.4.0...v0.5.0) (2021-10-08)


### Features

* add support for --version flag to CliHelper ([5abae87](https://github.com/PharmGKB/pgkb-common/commit/5abae8766e51fca9136e59c09f2646dba53a4c5f))

## [0.4.0](https://github.com/PharmGKB/pgkb-common/compare/v0.3.0...v0.4.0) (2021-09-05)


### Features

* merge pgkb-common-io into pgkb-common ([7f8cb61](https://github.com/PharmGKB/pgkb-common/commit/7f8cb6165d7d3555a46eb7b2b04ef319a54ebedd))


### Bug Fixes

* avoid NPE ([e042f06](https://github.com/PharmGKB/pgkb-common/commit/e042f06e690a4e6cd675aeb7b188144c5fe9e1a7))
* fix reserved arg check for custom options ([021eb37](https://github.com/PharmGKB/pgkb-common/commit/021eb3772b1c800b61bcb61ad42cd091aea5e411))
* fix zip file handling ([a0d0298](https://github.com/PharmGKB/pgkb-common/commit/a0d0298b1b51788218a0642417dbc3efcb63dccb))

## [0.3.0](https://github.com/PharmGKB/pgkb-common/compare/v0.2.2...v0.3.0) (2021-04-08)


### Features

* add ExtendedEnumHelper.lookupByNameOrThrow ([9624cfe](https://github.com/PharmGKB/pgkb-common/commit/9624cfea778659d72aa714ce424c59fa7bda87d5))
* add Strings utility class ([465785b](https://github.com/PharmGKB/pgkb-common/commit/465785b9c597330ef6124215b0f0dc2b212796be))


### Bug Fixes

* getFileExtension() must use lastIndexOf instead indexOf ([3ac2035](https://github.com/PharmGKB/pgkb-common/commit/3ac203570e4647c6db4625d6d689406b9cbed1f5))
* support haplotype names with genes ([5a8a9a3](https://github.com/PharmGKB/pgkb-common/commit/5a8a9a34b225661ad4eaf58a9553cdbae70856f7))
