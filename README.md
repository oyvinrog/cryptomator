[![vaultXP](cryptomator.png)](https://github.com/user/vaultXP)

# vaultXP - Experimental version of cryptomator

vaultXP is an experimental fork of Cryptomator, exploring new features and capabilities for cloud storage encryption.

---

## Introduction

vaultXP offers multi-platform transparent client-side encryption of your files in the cloud. This is an experimental version of Cryptomator with additional features and modifications.

Build vaultXP using Maven (instructions below).

## Features

- Works with Dropbox, Google Drive, OneDrive, MEGA, pCloud, ownCloud, Nextcloud and any other cloud storage service which synchronizes with a local directory
- Open Source means: No backdoors, control is better than trust
- Client-side: No accounts, no data shared with any online service
- Totally transparent: Just work on the virtual drive as if it were a USB flash drive
- AES encryption with 256-bit key length
- File names get encrypted
- Folder structure gets obfuscated
- Use as many vaults in your Dropbox as you want, each having individual passwords
- More than Five thousand commits for the security of your data!! :tada:

### Privacy

- 256-bit keys (unlimited strength policy bundled with native binaries)
- Scrypt key derivation
- Cryptographically secure random numbers for salts, IVs and the masterkey of course
- Sensitive data is wiped from the heap asap
- Lightweight: [Complexity kills security](https://www.schneier.com/essays/archives/1999/11/a_plea_for_simplicit.html)

### Consistency

- Authenticated encryption is used for file content to recognize changed ciphertext before decryption
- I/O operations are transactional and atomic, if the filesystems support it
- Each file contains all information needed for decryption (except for the key of course), no common metadata means no [SPOF](http://en.wikipedia.org/wiki/Single_point_of_failure)

### Security Architecture

For more information on the security details of the underlying Cryptomator architecture visit [cryptomator.org](https://docs.cryptomator.org/security/architecture/).

## Building

### Dependencies

* JDK 24 (e.g. temurin, zulu)
* Maven 3

### Run Maven

```
mvn clean install
# or mvn clean install -Pwin
# or mvn clean install -Pmac
# or mvn clean install -Plinux
```

This will build all the jars and bundle them together with their OS-specific dependencies under `target`. This can now be used to build native packages.

## License

This project is dual-licensed under the GPLv3 for FOSS projects as well as a commercial license for independent software vendors and resellers. If you want to modify this application under different conditions, feel free to contact our support team.
