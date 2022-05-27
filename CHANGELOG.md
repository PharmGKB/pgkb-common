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
