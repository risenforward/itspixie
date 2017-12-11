---
alias: ufeshusai8
description: YAML Structure
---

# YAML Structure

## Overview

The service definition file `graphcool.yml` has the following root properties:

- `service` (required): Name of the Graphcool service
- `datamodel` (required): Type definitions for database models, relations, enums and other types
- `stages` (required): List of different deployment targets ("stages")
- `disableAuth`: Disable authentication for the endpoint
- `secret`: Secret for securing the API endpoint
- `schema`: Determines where the CLI should store the database schema (typically in a file called `database.graphql`)
- `subscriptions`: Configuration of subscription functions
- `custom`: Use to provide variables which can be referenced from other fields

> The exact structure of `graphcool.yml` is defined with [JSON schema](http://json-schema.org/). You can find the corresponding schema definition [here](https://github.com/graphcool/graphcool-json-schema/blob/master/src/schema.json).

## `service` (required)

The service defines the service name which will also be reflected in the endpoint for your service once it's deployed. The service name must follow these requirements:

- must contain only alphanumeric characters, hyphens and undersroces
- must start with an uppercase or lowercase letter
- must be at most 64 characters long

#### Type

The `service` property expects a **string**.

#### Examples

Define a service called `hello-word`.

```yml
service: hello-world
```

Define a service called `DEMO_APP123`.

```yml
service: My-DEMO_APP123
```

## `datamodel` (required)

The `datamodel` points to one or more `.graphql`-files containing type definitions written in the [SDL](https://blog.graph.cool/graphql-sdl-schema-definition-language-6755bcb9ce51). If multiple files are provided, the CLI will simply concatenate their contents at deployment time.

#### Type

The `datamodel` property expects a **string** or a **list of strings**.

#### Examples

The data model is defined in a file called `types.graphql`.

```yml
datamodel: types.graphql
```

The data model is defined in two files called `types.graphql` and `enums.graphl`. When deployed, the contents of both files will be concatenated by the CLI.

```yml
datamodel:
  - types.graphql
  - enums.graphql
```

## `stages` (required)

The `stages` property defines a list of named deployment targets to which you can deploy your service.

#### Type

The `stages` property expects an **object**. The keys in that object represent the names of the available stages, the values are the identifiers of the corresponding cluster (loaded from the global `.graphcoolrc`).

Note that the object has one special key called `default` which points at the default deployment target.

#### Examples

Define two stages called `dev` and `prod` where `dev` is set as the default deployment target. Note that this example expects two entries in the global `.graphcoolrc` file called `local` and `london-cluster`.

```yml
stages:
  default: dev
  dev: local
  prod: london-cluster
```

## `disableAuth` (optional)

The `disableAuth` property indicates whether the Graphcool service requires authentication. If set to `true`, anyone who has access to the service's endpoint has full read/write-access to the database!

Setting `disableAuth` is optional. If not set, the default is `false` (which means authentication is enabled by default).

#### Type

The `disableAuth` property expects a **boolean**.

#### Examples

Disable authentication in your Graphcool service.

```yml
disableAuth: true
```

Enable authentication in your Graphcool service.

```yml
disableAuth: false
```

## `secret` (optional)

A secret is used to generate (_sign_) authentication tokens ([JWT](https://jwt.io)). If your Graphcool service requires authentication, one of these authentication tokens needs to be attached to the HTTP request (in the `Authorization` header field). Note that it's possible to specify multiple secrets. Each secret must follow these requirements:

- must be [utf8](https://en.wikipedia.org/wiki/UTF-8) encoded
- must not contain spaces
- must be at most 256 characters long

The JWT sent to the API needs to meet the following conditions:

- must be signed with a `secret` configured for the service
- must contain an `exp` claim with a value in the future
- must contain a `service` claim with service and stage matching the current request
- must contain a `roles` claim that provides access to the current operation

#### Type

The `secret` property expects a **string** (not a list of strings). If you want to specify multiple secrets, you need to provide them as a comma-separated list (spaces are ignored), but still as a single string value.

#### Examples

Define one secret with value `moo4ahn3ahb4phein1eingaep`.

```yml
secret: moo4ahn3ahb4phein1eingaep
```

Define three secrets with values `myFirstSecret`, `SECRET_NUMBER_2` and `3rd-secret`. Note that the spaces before the second secret are ignored.

```yml
secret: myFirstSecret,    SECRET_NUMBER_2,3rd-secret
```

Use the value of the `MY_SECRET` environment variable as the value for  `disableAuth`.

```yml
secret: ${env:MY_SECRET}
```

## `schema` (optional)

Upon every deploy of your service, the CLI will generate the service's _database schema_ (typically called `database.graphql`). This file contains the definitions of all CRUD operations for the types defined in your data model.

The `schema` property specifies the file path indicating where the CLI should store the generated file.

Specifying `schema` is optional. If not set, the CLI will not generate and store the database schema!

#### Type

The `schema` property expects a **string**.

#### Example

Store the database schema in the root directory of the service and call it `database.graphql`.

```yml
schema: database.graphql
```

Store the database schema in the `src/schemas` directory of the service and call it `database.graphql`.

```yml
schema: src/schemas/database.graphql
```

## `subscriptions` (optional)

The `subscriptions` property is used to define all the event subscription functions for your Graphcool service. Event subscriptions need (at least) two pieces of information:

- a _subscription query_ that defines upon which event a function should be invoked and what the payload looks like
- the URL of a _webhook_ which is invoked via HTTP once the event happens
- (optionally) a number of HTTP headers to be attached to the request that's sent to the URL

#### Type

The `subscriptions` property expects an **object** with the following properties:

- `query` (required): The file path to the _subscription query_.
- `webhook` (required): Information (URL and optionally HTTP headers) about the webhook to be invoked. If there are no headers, you can provide the URL to this property directly (see first example below). Otherwise, `webhook` takes another object with properties `url` and `headers` (see second example below).

#### Examples

Specify one event subscription without HTTP headers.

```yml
subscriptions:
  sendWelcomeEmail:
      query: database/subscriptions/sendWelcomeEmail.graphql
      webhook: https://bcdeaxokbj.execute-api.eu-west-1.amazonaws.com/dev/sendWelcomeEmail
```

Specify one event subscription with two HTTP headers.

```yml
subscriptions:
  sendWelcomeEmail:
      query: database/subscriptions/sendWelcomeEmail.graphql
      webhook:
        url: https://bcdeaxokbj.execute-api.eu-west-1.amazonaws.com/dev/sendWelcomeEmail
        headers:
          Authorization: ${env:MY_ENDPOINT_SECRET}
          Content-Type: application/json
```

## `custom` (optional)

The `custom` property lets you specify any sorts of values you want to resuse elsewhere in your `graphcool.yml`. It thus doesn't have a predefined structure. You can reference the values using variables with the `self` variable source, e.g.: `${self.custom.myVariable}`.

#### Type

The `custom` property expects an **object**. There are no assumptions about the shape of the object.

#### Examples

Define two custom values and reuse them in the definition of the event subscription.

```yml
custom:
  serverlessEndpoint: https://bcdeaxokbj.execute-api.eu-west-1.amazonaws.com/dev
  subscriptionQueries: database/subscriptions/

subscriptions:
  sendWelcomeEmail:
    query: ${subscriptionQueries}/sendWelcomeEmail.graphql
    webhook: https://${self.custom:serverlessEndpoint}/sendWelcomeEmail
```