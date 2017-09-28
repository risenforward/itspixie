<p align="center"><a href="https://www.graph.cool"><img src="https://imgur.com/vXOT4eF.png" width="314"></a></p>

[![CircleCI](https://circleci.com/gh/graphcool/graphcool.svg?style=shield)](https://circleci.com/gh/graphcool/graphcool) [![Slack Status](https://slack.graph.cool/badge.svg)](https://slack.graph.cool)

## Quickstart

[Watch this 2 min tutorial](https://www.youtube.com/watch?v=gg_SJ8a5xpA) or follow the steps below to get started with the Graphcool framework:

1. **Install via NPM:**

  ```sh
  npm install -g graphcool
  ```

2. **Init new project:**

  ```sh
  graphcool init
  ```
  
3. **Configure data model & project:**

  Edit `types.graphql` to define your data model and setup functions & permissions in  `graphcool.yml`.
  
  The `graphcool.yml` file is the core of your application and should look like this:
  
  ```yml
  # Define your data model here
  types: types.graphql

  # Configure the permissions for your data model
  permissions:
  - operation: "*"

  # tokens granting root level access to your API
  rootTokens: []

  # You can implement your business logic using functions
  functions:
    hello:
      handler:
        code:
          src: ./code/hello.js
      type: resolver
      schema: ./code/hello.graphql
  ```
  
4. **Deploy your changes:**

  You can deploy your changes and migrate your database by running the following command:
  
  ```sh
  graphcool deploy
  ```
  
5. **Connect to your GraphQL endpoint:**

  Use the endpoint from step (2) in your frontend (or backend) applications to connect to your GraphQL API.

## Features

## Examples

## Contributing

Your feedback is **very helpful**, please share your opinion and thoughts! 🤔

### +1 an issue

If an existing feature request or bug report is very important for you, please go ahead and :+1: it or leave a comment. We're always open to reprioritize our roadmap to make sure you're having the best possible DX.

### Requesting a new feature

We love your ideas for new features. If you're missing a certain feature, please feel free to [request a new feature here](https://github.com/graphcool/feature-requests/issues/new). (Please make sure to check first if somebody else already requested it.)

## Keep up to date

* Subscribe to the features you're interested in
* Check [the changelog](https://www.graph.cool/docs/faq/graphcool-changelog-chiooo0ahn/) to see what we've recently built
