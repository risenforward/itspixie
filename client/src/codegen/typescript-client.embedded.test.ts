import * as fs from 'fs'
import * as path from 'path'
import { buildSchema } from 'graphql'
import { TypescriptGenerator } from './typescript-client'
import { test } from 'ava'
import { parseInternalTypes, DatabaseType } from 'prisma-generate-schema'

const typeDefs = fs.readFileSync(
  path.join(__dirname, '../../src/codegen/fixtures/embedded.graphql'),
  'utf-8',
)

const datamodel = `
type User {
  id: ID! @unique
  name: String!
  address: Address
}

type Address @embedded {
  location: String
}
`

test('typescript generator - embedded', t => {
  const schema = buildSchema(typeDefs)
  const generator = new TypescriptGenerator({
    schema,
    internalTypes: parseInternalTypes(datamodel, DatabaseType.relational),
  })
  const result = generator.render()
  t.snapshot(result)
})
