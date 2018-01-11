const { GraphQLServer } = require('graphql-yoga')
const { Graphcool } = require('graphcool-binding')
const { me, signup, login, updatePassword, AuthPayload } = require('./auth')
const { createPost, updatePost, deletePost, posts } = require('./posts')
const { user } = require('./users')


const resolvers = {
  Query: {
    me,
    posts,
    user,
  },
  Mutation: {
    signup,
    login,
    updatePassword,
    createPost,
    updatePost,
    deletePost,
  },
}

const server = new GraphQLServer({
  typeDefs: './src/schema.graphql',
  resolvers,
  context: req => ({
    ...req,
    db: new Graphcool({
      typeDefs: 'src/generated/graphcool.graphql',
      endpoint: process.env.GRAPHCOOL_ENDPOINT,
      secret: process.env.GRAPHCOOL_SECRET,
      debug: true,
    }),
  }),
})

server.start(() => console.log('Server is running on http://localhost:4000'))
