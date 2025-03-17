# OpenBAS Documentation Space

[![Website](https://img.shields.io/badge/website-openbas.io-blue.svg)](https://openbas.io)
[![Slack Status](https://img.shields.io/badge/slack-3K%2B%20members-4A154B)](https://community.filigran.io)

## Introduction

This is the main repository of the OpenBAS Documentation space. The online version is available directly on [docs.openbas.io](https://docs.openbas.io).

## Install the documentation locally

Clone the repository:
```
$ git clone git@github.com:OpenBAS-Platform/docs.git
```

Install dependencies
```
pip install -r requirements.txt
```

Upgrade dependencies
```
pip install --upgrade -r requirements.txt
pip install --upgrade git+https://TOKEN@github.com/squidfunk/mkdocs-material-insiders.git
```

Launch the local environment:
```
$ mkdocs serve
Starting server at http://localhost:8000/
```

## Deploy the documentation

### Update the source

Commiting on the main branch does not impact (for now) the deployed documentation, please commit as many times as possible:
```
$ git commit -a -m "[docs] MESSAGE"
$ git push
```

### Deploy and update the current version

With the right version number (eg. 3.3.X):
```
$ mike deploy --push [version]
```

### Deploy a new stable version

With the right version number (eg. 3.3.X), update the `latest` tag:
```
$ mike deploy --push --update-aliases [version] latest
```

## Useful commands

List versions:
```
$ mike list
```
