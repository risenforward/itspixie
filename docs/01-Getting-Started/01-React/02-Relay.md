---
alias: sot2faez6a
description: Get started in 5 min with React, GraphQL and Relay Modern by building a simple Instagram clone.
---

# React & Relay Quickstart


For this quickstart tutorial, we have prepared a [repository](https://github.com/graphcool-examples/react-graphql/tree/master/quickstart-with-relay) that contains the full React code for the Instagram clone. All you need to do is create the Graphcool service that will expose the GraphQL API and connect it with the React application. Let's get started! 

<Instruction>

Clone the example repository that contains the React application:

```sh
git clone https://github.com/graphcool-examples/react-graphql.git
cd react-graphql/quickstart-with-relay
```

</Instruction>

Feel free to get familiar with the code. The app contains the following React [`components`](https://github.com/graphcool-examples/react-graphql/tree/master/quickstart-with-relay/src/components):

- `Post`: Renders a single post item
- `ListPage`: Renders a list of post items
- `CreatePage`: Allows to create a new post item
- `DetailPage`: Renders the details of a post item and allows to update and delete it

Graphcool services are managed with the [Graphcool CLI](!alias-zboghez5go). So before moving on, you first need to install it.

<Instruction>

Install the Graphcool CLI:

```sh
npm install -g graphcool@next
```

</Instruction>

Now that the CLI is installed, you can use it to create a new service with the [`graphcool init`](!alias-zboghez5go#graphcool-init) command.

<Instruction>

Create a new _blank_ Graphcool service inside a directory called `server`:

```sh(path="")
# Create a local service definition in a new directory called `server`
graphcool init server
```

</Instruction>

> Note: If you haven't authenticated with the Graphcool CLI before, this command is going to open up a browser window and ask you to login. Your authentication token will be stored in the global `~/.graphcoolrc`.

`graphcool init` creates the local service structure inside the specified `server` directory:

```(nocopy)
.
└── server
    ├── graphcool.yml
    ├── types.graphql
    ├── .graphcoolrc
    └── src
        ├── hello.graphql
        └── hello.js
```

Each of the created files and directories have a dedicated purpose inside your Graphcool service:

- `graphcool.yml`: Contains your [service definition](!alias-opheidaix3).
- `types.graphql`: Contains all the type definitions for your service, written in the GraphQL [Schema Definitiona Language](https://medium.com/@graphcool/graphql-sdl-schema-definition-language-6755bcb9ce51) (SDL).
- `.graphcoolrc` (local): Contains information about the [targets](!alias-zoug8seen4) that you have configured for your service.
- `src`: Contains the source code (and if necessary GraphQL queries) for the [functions](!alias-aiw4aimie9) you've configured for your service. Notice that a new service comes with a default "Hello World"-function which you can delete if you don't want to use it.

Next you need to configure the [data model](!alias-eiroozae8u) for your service.

<Instruction>

Open `./graphcool/types.graphql` and add the following type definition to it:

```graphql(path="graphcool/types.graphql")
type Post {
  id: ID! @isUnique
  createdAt: DateTime!
  updatedAt: DateTime!
  description: String!
  imageUrl: String!
}
```

</Instruction>

The changes you introduced by adding the `Post` type to the data model are purely _local_ so far. So the next step is to actually _deploy_ the service!

<Instruction>

Navigate to the `server` directory and deploy your service:

```sh(path="")
cd server
graphcool deploy
```

</Instruction>

The `Post` type is now added to your data model and the corresponding CRUD operations are generated and exposed by the GraphQL API.

<Instruction>

<InfoBox type=info>

Since you're using Relay in this project, you need to use the endpoint for the `Relay API`. Relay has a number of requirements for how a GraphQL API needs to be structured (e.g. exposing relations as _connections_). These requirements are implemented in the Graphcool `Relay API`.

</InfoBox>


</Instruction>

You can test the API inside a [GraphQL Playground](https://github.com/graphcool/graphql-playground) which you can open with the `graphcool playground` command. Try out the following query and mutation.

**Fetching all posts:**

```graphql
query {
  allPosts {
    id
    description
    imageUrl
  }
}
```

**Creating a new post:**

```graphql
mutation {
  createPost(
    description: "A rare look into the Graphcool office"
    imageUrl: "https://media2.giphy.com/media/xGWD6oKGmkp6E/200_s.gif"
  ) {
    id
  }
}
```

![](https://imgur.com/w95UEi9.gif)

> Notice that a Playground by default is opened for the `Simple API` - not the `Relay API`. Queries and mutation in the `Relay API` look slightly different (but are of course accessing the same database).

The next step is to connect the React application with the GraphQL API from your Graphcool service.


<Instruction>

You therefore first need to get access to the HTTP endpoint of the `Relay API` which you can do with the following command:

```sh(path="server")
graphcool info
```

</Instruction>

<Instruction>

Now paste the `Relay API` endpoint to `./src/createRelayEnvironment.js` as the argument for the call to `fetch` replacing `__RELAY_API_ENDPOINT__ `:

```js
// replace `__RELAY_API_ENDPOINT__ ` with the endpoint from the previous step
return fetch('__RELAY_API_ENDPOINT__', {
 ...
})  
```

</Instruction>

That's it. The last thing to do is actually run the application 🚀

<Instruction>

Install dependencies and run the app:

```sh(path="")
yarn install
yarn relay # invoke relay compiler
yarn start # open http://localhost:3000 in your browser
```

</Instruction>


### Learn more

* [Advanced GraphQL features](https://blog.graph.cool/advanced-graphql-features-of-the-graphcool-api-5b8db3b0a71)
* [Authentication & Permissions](https://www.graph.cool/docs/reference/auth/overview-ohs4aek0pe/)
* [Implementing business logic with serverless functions](https://www.graph.cool/docs/reference/functions/overview-aiw4aimie9/)
