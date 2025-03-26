# Changelog

All notable changes to this project will be documented in this file. See [commit-and-tag-version](https://github.com/absolute-version/commit-and-tag-version) for commit guidelines.

## [0.4.0](https://github.com/eclipse-kuksa/kuksa-java-sdk/compare/release/release/v0.3.2...release/v0.4.0) (2025-03-26)

### Features

* Introduce Common Classes for kuksa.val.v1 and kuksa.val.v2 ([81d9f82](https://github.com/eclipse-kuksa/kuksa-java-sdk/commit/81d9f8236269ba72020a3ecaad9b9d86bdfda6a2))


### ⚠ BREAKING CHANGES

* Remove DataBrokerSubscriber ([4be2081](https://github.com/eclipse-kuksa/kuksa-java-sdk/commit/4be20818617e160ae95775a677c8eb2c12011a91))
* DataBrokerConnection#unsubscribe was removed
the unsubscribe method now returns a CancelableContext which
must be stored by the user, if canceling the subscription is needed.
* kuksa.val.v1 and kuksa.val.v2 specific classes were slimlined
- DataBrokerConnector: Package has been changed
- DataBrokerConnection: Package has been changed
- DataBrokerConnection: Api specific calls were moved to DatabrokerConnection#kuksaValV1 resp. DatabrokerConnection#kuksaValV2
- DataBrokerConnectorV2: Removed
- DataBrokerConnectionV2: Removed

## [0.3.2](https://github.com/eclipse-kuksa/kuksa-java-sdk/compare/release/release/v0.3.1...release/v0.3.2) (2025-02-20)


### Bug Fixes

* Dependency Clash with annotations-api ([32f33e1](https://github.com/eclipse-kuksa/kuksa-java-sdk/commit/32f33e18f4e107cb2cdb3c50060eb3b412de7339))

## 0.3.1 (2025-01-15)


### Features

* First Release as a plain java library
* Add Support for kuksa.val.v2 Protocol ([b2a33e5](https://github.com/eclipse-kuksa/kuksa-java-sdk/commit/b2a33e516846d5c1ad849afe521f1d339ac1d606))


### Documentation

* Add kuksa.val.v2 Documentation to README ([e4ea538](https://github.com/eclipse-kuksa/kuksa-java-sdk/commit/e4ea5384bd72461f7084e0ba982b35d347777be9))
* Add kuksa.val.v2 Samples ([70c43f1](https://github.com/eclipse-kuksa/kuksa-java-sdk/commit/70c43f1cad8b830e2fd273ccc9fe9a7987cc04d6))
