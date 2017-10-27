// import * as express from 'express'
// import * as cors from 'cors'
// import * as bodyParser from 'body-parser'
// import { graphqlExpress } from 'apollo-server-express'
// import { transformSchema } from 'graphql-transform-schema'
// import { makeRemoteExecutableSchema, mergeSchemas, introspectSchema } from 'graphql-tools'
// import { HttpLink } from 'apollo-link-http'
// import fetch from 'node-fetch'
// import { express as playground } from 'graphql-playground/middleware'

const express = require('express')
const cors = require('cors')
const bodyParser = require('bodyParser')
const graphqlExpress = require('apollo-server-express').graphqlExpress
const transformSchema = require('graphql-transform-schema').transformSchema
const makeRemoteExecutableSchema = require('graphql-tools').makeRemoteExecutableSchema
const mergeSchemas = require('graphql-tools').mergeSchemas
const introspectSchema = require('graphql-tools').introspectSchema
const HttpLink = require('apollo-link-http').HttpLink
const fetch = require('node-fetch')
const playground  = require('graphql-playground/middleware').express

function run() {

    // Step 1: Create local version of the CRUD API
    const endpoint = '__SIMPLE_API_ENDPOINT__'  // looks like: https://api.graph.cool/simple/v1/__SERVICE_ID__
    const link = new HttpLink({ uri: endpoint, fetch })
    introspectSchema(link).then(schema => {
        const graphcoolSchema = makeRemoteExecutableSchema({
            schema,
            link
        })

        // Step 2: Define schema for the new API
        const extendTypeDefs = `
          extend type Query {
            viewer: Viewer!
          }
      
          type Viewer {
            me: User
            topPosts(limit: Int): [Post!]!
          }
        `

        // Step 3: Merge remote schema with new schema
        const mergedSchemas = mergeSchemas({
            schemas: [graphcoolSchema, extendTypeDefs],
            resolvers: mergeInfo => ({
                Query: {
                    viewer: () => ({}),
                },
                Viewer: {
                    me: {
                        resolve(parent, args, context, info) {
                            const alias = 'john' // should be determined from context
                            return mergeInfo.delegate('query', 'User', { alias }, context, info)
                        },
                    },
                    topPosts: {
                        resolve(parent, { limit }, context, info) {
                            return mergeInfo.delegate('query', 'allPosts', { first: limit }, context, info)
                        },
                    },
                },
            }),
        })

        // Step 4: Limit exposed operations from merged schemas
        // Hide every root field except `viewer`
        const schema = transformSchema(mergedSchemas, {
            '*': false,
            viewer: true,
        })

        const app = express()

        app.use('/graphql', cors(), bodyParser.json(), graphqlExpress({ schema }))
        app.use('/playground', playground({ endpoint: '/graphql' }))

        app.listen(3000, () => console.log('Server running. Open http://localhost:3000/playground to run queries.'))
    })

}

run().catch(console.error.bind(console))
