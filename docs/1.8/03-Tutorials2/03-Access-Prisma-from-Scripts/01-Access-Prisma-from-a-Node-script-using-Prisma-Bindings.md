---
alias: vbadiyyee9
description: Learn how to access Prisma from a Node script using Prisma Bindings.
---

# Access Prisma from a Node script using Prisma Bindings

This tutorial teaches you how to access an existing Prisma service from a simple Node script. You will access your Prisma service using a nice API generated with _Prisma bindings_.

The tutorial assumes that you already have a running Prisma service, so please make sure to have the _endpoint_ of it available. If you're unsure about how you can get started with your own Prisma service, check one of these tutorials:

* [Setup Prisma on a Demo server](!alias-ouzia3ahqu)
* [Setup Prisma with a new MySQL Database](!alias-gui4peul2u)
* [Setup Prisma with a new Postgres Database](!alias-eiyov7erah)
* [Setup Prisma by connecting your empty MySQL Database](!alias-dusee0nore)
* [Setup Prisma by connecting your empty Postgres Database](!alias-aiy1jewith)

<InfoBox>

To ensure you're not accidentally skipping an instruction in the tutorial, all required actions are highlighted with a little _counter_ on the left.
<br />
💡 **Pro tip**: If you're only keen on getting started but don't care so much about the explanations of what's going on, you can simply jump from instruction to instruction.

</InfoBox>

## Step 1: Update the data model

You already have your existing Prisma service, but for this tutorial we need to make sure that it has the right data model for the upcoming steps.

Note that we're assuming that your data model lives in a single file called `datamodel.graphql`. If that's not the case, please adjust your setup.

<Instruction>

Open `datamodel.graphql` and update its contents to look as follows:

```graphql
type User {
  id: ID! @unique
  name: String!
  posts: [Post!]!
}

type Post {
  id: ID! @unique
  title: String!
  content: String!
  published: Boolean! @default(value: "false")
  author: User!
}
```

</Instruction>

<Instruction>

After you saved the file, open your terminal and navigate into the root directory of your Prisma service (the one where `prisma.yml` is located) and run the following command to update its GraphQL API:

```sh
prisma deploy
```

</Instruction>

The GraphQL API of your Prisma service now exposes CRUD operations for the `User` as well as the `Post` type that are defined in your data model and also lets you modify _relations_ between them.

## Step 2: Setup directory

Now that you have updated your data model, you will setup your directory structure.

<Instruction>

Navigate into a new directory and paste the following commands in your terminal:

```sh
mkdir -p my-node-script/src
touch my-node-script/src/index.js
cd my-node-script
yarn init -y
```

</Instruction>

<Instruction>

Next, move the root directory of your Prisma service into `my-node-script` and rename it to `prisma`.

```sh
cd ..
mkdir my-node-script/prisma
mv datamodel.graphql prisma.yml my-node-script/prisma
cd my-node-script
```

</Instruction>

```
.
└── my-node-script
    ├── package.json
    ├── prisma
    │   ├── datamodel.graphql
    │   └── prisma.yml
    └── src
        └── index.js
```

<Instruction>

Next, install `prisma-binding`.

```sh
yarn add prisma-binding graphql
```

</Instruction>

## Step 3: Download the Prisma database schema

The next step is to download the GraphQL schema of Prisma's GraphQL API (also referred to as _Prisma database schema_) into your project so you can point Prisma binding to them.

Downloading the Prisma database schema is done using the [GraphQL CLI](https://oss.prisma.io/content/GraphQL-CLI/01-Overview.html) and [GraphQL Config](https://oss.prisma.io/content/GraphQL-Config/Overview.html).

<Instruction>

Install the GraphQL CLI using the following command:

```sh
yarn global add graphql-cli
```

</Instruction>

<Instruction>

Next, create your `.graphqlconfig` in the root directory of the server (i.e. in the `my-yoga-server` directory):

```sh
touch .graphqlconfig.yml
```

</Instruction>

<Instruction>

Put the following contents into it, defining the two GraphQL APIs you're working with in this project (Prisma's GraphQL API as well as the customized API of your `graphql-yoga` server):

```yml
projects:
  app:
    schemPath: src/schema.graphql
    extensions:
      endpoints:
        default: http://localhost:4000
  prisma:
    schemaPath: src/generated/prisma.graphql
    extensions:
      prisma: prisma/prisma.yml
```

</Instruction>

The information you provide in this file is used by the GraphQL CLI as well as the GraphQL Playground. In the Playground specifically it allows you to work with both APIs side-by-side.

<Instruction>

To download the Prisma database schema to `src/generated/prisma.graphql`, run the following command in your terminal:

```sh
graphql get-schema --project prisma
```

</Instruction>

The Prisma database schema which defines the full CRUD API for your database is now available in the location you specified in the `projects.prisma.schemaPath` property in your `.graphqlconfig.yml` (which is `src/generated/prisma.graphql`) and the import statements will work properly.

<InfoBox>

💡 **Pro tip**: If you want the Prisma database schema to update automatically every time you deploy changes to your Prisma services (e.g. an update to the data model), you can add the following post-deployment [hook](!alias-ufeshusai8#hooks-optional) to your `prisma.yml` file:

```yml
hooks:
  post-deploy:
    - graphql get-schema -p prisma
```

</InfoBox>

## Step 4: Send queries and mutations

In this step you will communicate with your Prisma service using Prisma binding.

Instantiate a Prisma binding by pointing it to the schema you downloaded in the previous step. You also need to point it to your Prisma URL, which is stored in `prisma/prisma.yml`.

The Prisma bindings instance acts as a Javascript SDK of your Prisma service. You can use this expressive API to send queries and mutations to your Prisma database.

<Instruction>

Put the following code in `src/index.js`. It instantiates a Prisma binding instance and uses it to send queries and mutations to your Prisma service.

<InfoBox type=warning>

⚠️ **Important**: Remember to replace the value of `__YOUR_PRISMA_ENDPOINT__` with your Prisma endpoint, which is stored in `prisma/prisma.yml`.

</InfoBox>

```js
const { Prisma } = require("prisma-binding");

const prisma = new Prisma({
  typeDefs: "src/generated/prisma.graphql",
  endpoint: "__YOUR_PRISMA_ENDPOINT__"
});

prisma.mutation
  .createUser({ data: { name: "Alice" } }, "{ id name }")
  .then(console.log)
  // { id: 'cjhcidn31c88i0b62zp4tdemt', name: 'Alice' }
  .then(() => prisma.query.users(null, "{ id name }"))
  .then(response => {
    console.log(response);
    // [ { id: 'cjhcidn31c88i0b62zp4tdemt', name: 'Alice' } ]
    return prisma.mutation.createPost({
      data: {
        title: "Prisma rocks!",
        content: "Prisma rocks!",
        author: {
          connect: {
            id: response[0].id
          }
        }
      }
    });
  })
  .then(response => {
    console.log(response);
    /*
      { id: 'cjhcidoo5c8af0b62kv4dtv3c',
        title: 'Prisma rocks!',
        content: 'Prisma rocks!',
        published: false }
    */
    return prisma.mutation.updatePost({
      where: { id: response.id },
      data: { published: true }
    });
  })
  .then(console.log)
  /*
    { id: 'cjhcidoo5c8af0b62kv4dtv3c',
      title: 'Prisma rocks!',
      content: 'Prisma rocks!',
      published: true }
  */
  .then(() => prisma.query.users(null, "{ id posts { title } }"))
  .then(console.log)
  // [ { id: 'cjhcidn31c88i0b62zp4tdemt', posts: [ [Object] ] } ]
  .then(() => prisma.mutation.deleteManyPosts())
  .then(console.log)
  // { count: 1 }
  .then(() => prisma.mutation.deleteManyUsers())
  .then(console.log);
// { count: 1 }
```

</Instruction>

<Instruction>

Run the script to see the query results printed in your terminal.

```bash
node src/index.js
```

</Instruction>

## Step 5: Checking for existence

Besides query and mutation, Prisma binding provides a handy property called `exists`. Just like query or mutation, it contains several auto generated functions. It generates a function for every type in your schema. These functions receive filter arguments and return `true` or `false`.

Because the schema you created in Step 1 has two models, User and Post, Prisma generated `exists.User` and `exists.Post`.

<Instruction>

Put the following code in `src/index.js`. It instantiates a Prisma binding instance and uses it to send queries and mutations to your Prisma service.

<InfoBox type=warning>

⚠️ **Important**: Remember to replace the value of `__YOUR_PRISMA_ENDPOINT__` with your Prisma endpoint, which is stored in `prisma/prisma.yml`.

</InfoBox>

```js
const { Prisma } = require("prisma-binding");

const prisma = new Prisma({
  typeDefs: "src/generated/prisma.graphql",
  endpoint: "__YOUR_PRISMA_ENDPOINT__"
});

prisma.mutation
  .createUser({ data: { name: "Alice" } }, "{ id name }")
  .then(response => {
    return prisma.mutation.createPost({
      data: {
        title: "Prisma rocks!",
        content: "Prisma rocks!",
        author: {
          connect: {
            id: response.id
          }
        }
      }
    });
  })
  .then(() => prisma.exists.User({ name: "Alice" }))
  .then(response => console.log(response))
  // true
  .then(() => prisma.exists.Post({ title: "Prisma rocks" }))
  .then(response => console.log(response))
  // true
  .then(() => prisma.mutation.deleteManyPosts())
  .then(() => prisma.mutation.deleteManyUsers())
  .then(() => prisma.exists.Post({ title: "Prisma rocks" }))
  .then(response => console.log(response))
  // false
  .then(() => prisma.exists.User({ name: "Alice" }))
  .then(console.log);
  // false
```

</Instruction>

<Instruction>

Run the script to see the query results printed in your terminal.

```bash
node src/index.js
```

</Instruction>

## Step 6: Send raw queries and mutations

Prisma binding also lets you send queries and mutations to your Prisma service. They work exactly the same as the functions generated based on your schema, but they are a little more verbose.

Using request is more verbose because you need to give them the full query/mutation, and also their responses have a little more overhead because they include the name of the query/mutation as a top level key.

Prisma binding's `request` uses [`graphql-request`](https://github.com/graphcool/graphql-request) under the hood.

<Instruction>

Replace the contents of `src/index.js` with the following code.

<InfoBox type=warning>

⚠️ **Important**: Remember to replace the value of `__YOUR_PRISMA_ENDPOINT__` with your Prisma endpoint, which is stored in `prisma/prisma.yml`.

</InfoBox>

```js
const { Prisma } = require("prisma-binding");

const prisma = new Prisma({
  typeDefs: "src/generated/prisma.graphql",
  endpoint: "__YOUR_PRISMA_ENDPOINT__"
});

const query = `
  {
    users {
      id
      name
    }
  }
`;
const mutation = `
  mutation CreateUser($name: String!) {
    createUser(data: { name: $name }) {
      id
      name
    }
  }
`;

const variables = { name: 'Bob' };

prisma.mutation
  .createUser({ data: { name: 'Alice' } }, '{ id name }')
  .then(console.log)
  // { id: 'cjhcijh30cgww0b622rwkkvbo', name: 'Alice' }
  .then(() => prisma.request(mutation, variables))
  .then(console.log)
  // { createUser: { id: 'cjhcijjndch0d0b62qux6o52a', name: 'Bob' } }
  .then(() => prisma.query.users(null, '{ id name }'))
  .then(console.log)
  /*
    [ { id: 'cjhciiacxcf850b62mcuaa3uz', name: 'Alice' },
      { id: 'cjhcijjndch0d0b62qux6o52a', name: 'Bob' } ]
  */
  .then(() => prisma.request(query))
  .then(console.log)
  /*
    { users:
      [ { id: 'cjhciiacxcf850b62mcuaa3uz', name: 'Alice' },
        { id: 'cjhcijjndch0d0b62qux6o52a', name: 'Bob' } ] }
  */
```

</Instruction>

<Instruction>

Run the script to see the query results printed in your terminal.

```bash
node src/index.js
```

</Instruction>
