# OpenBAS Documentation Space

[![Website](https://img.shields.io/badge/website-openbas.io-blue.svg)](https://openbas.io)
[![Slack Status](https://img.shields.io/badge/slack-3K%2B%20members-4A154B)](https://community.filigran.io)

## Introduction

This is the main repository of the OpenBAS Documentation space. The online version is available directly on [docs.openbas.io](https://docs.openbas.io).

## Development (local setup)
### Prerequisites
* Python 3

### Build and serve locally

**Unix-like (Linux, BSD, macOS...)**

Run this shell script from the root of the cloned repository:
```shell
$ ./serve.sh
```
This will create a virtual environment and install the dependencies, and serve the docs at http://localhost:8000.

**Windows**

Please follow the Manual instructions below.

### Alternative: manual local build and serve
Clone the repository:
```shell
$ git clone git@github.com:OpenBAS-Platform/docs.git
```

Install dependencies; consider a virtual environment for doing so

```shell
$ cd docs/
$ python -m venv .venv
$ source .venv/bin/activate
$ pip install -r requirements.txt
```

Launch the local environment (if you have created a virtual environment,
remember to activate it as above):
```shell
$ mkdocs serve
Starting server at http://localhost:8000/
```

Upgrade dependencies
```shell
$ cd docs/
$ source .venv/bin/activate
$ pip install --upgrade -r requirements.txt
$ pip install --upgrade git+https://TOKEN@github.com/squidfunk/mkdocs-material-insiders.git
```

## Deploy the documentation

### Update the source

Commiting on the main branch does not impact (for now) the deployed documentation,
please commit as many times as possible:
```shell
$ git commit -a -m "[docs] MESSAGE"
$ git push
```

### Deploy and update the current version

With the right version number (e.g. 3.3.X):
```shell
$ mike deploy --push [version]
```

### Deploy a new stable version

With the right version number (e.g. 3.3.X), update the `latest` tag:
```
$ mike deploy --push --update-aliases [version] latest
```

## Useful commands

List known versions:
```
$ mike list
```
