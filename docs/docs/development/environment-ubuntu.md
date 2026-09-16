# Prerequisites Ubuntu

## Backend
* git, OpenJDK >= 21 and Maven via `apt-get`:
```shell
apt-get update && apt-get install git openjdk-21-jdk maven
```
* Docker Engine: follow the official instructions at [Install Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/).

## Frontend
* yarn via `apt-get`

```shell
apt-get update && apt-get install yarnpkg
```

* Node.js >= 24 via `nvm`

Install `nvm` via its install script: [Installing and updating nvm](https://github.com/nvm-sh/nvm/blob/master/README.md#installing-and-updating).

Then install and set as default Node.js 24 (the version used by the CI and the Docker images):
```shell
nvm install 24
nvm use 24
```