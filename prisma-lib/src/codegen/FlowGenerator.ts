import {
  isNonNullType,
  isListType,
  isScalarType,
  isObjectType,
  isEnumType,
  GraphQLObjectType,
  GraphQLSchema,
  GraphQLUnionType,
  GraphQLInterfaceType,
  GraphQLInputObjectType,
  GraphQLInputField,
  GraphQLField,
  GraphQLInputType,
  GraphQLOutputType,
  GraphQLWrappingType,
  GraphQLNamedType,
  GraphQLScalarType,
  GraphQLEnumType,
  GraphQLFieldMap,
  GraphQLObjectType as GraphQLObjectTypeRef,
  printSchema,
} from 'graphql'

import { Generator } from './Generator'
import { getExistsFlowTypes } from '../utils'

export interface RenderOptions {
  endpoint?: string
  secret?: string
}

export class FlowGenerator extends Generator {
  scalarMapping = {
    Int: 'number',
    String: 'string',
    ID: 'string | number',
    Float: 'number',
    Boolean: 'boolean',
    DateTime: 'string',
    Json: 'any',
  }

  graphqlRenderers = {
    GraphQLUnionType: (type: GraphQLUnionType): string => {
      return `${this.renderDescription(type.description!)}export type ${
        type.name
      } = ${type
        .getTypes()
        .map(t => t.name)
        .join(' | ')}`
    },

    GraphQLObjectType: (
      type:
        | GraphQLObjectTypeRef
        | GraphQLInputObjectType
        | GraphQLInterfaceType,
    ): string => {
      return (
        this.renderInterfaceOrObject(type, true) +
        '\n\n' +
        this.renderInterfaceOrObject(type, false)
      )
    },

    GraphQLInterfaceType: (
      type:
        | GraphQLObjectTypeRef
        | GraphQLInputObjectType
        | GraphQLInterfaceType,
    ): string => this.renderInterfaceOrObject(type),

    GraphQLInputObjectType: (
      type:
        | GraphQLObjectTypeRef
        | GraphQLInputObjectType
        | GraphQLInterfaceType,
    ): string => {
      const fieldDefinition = Object.keys(type.getFields())
        .map(f => {
          const field = type.getFields()[f]
          const isOptional = !isNonNullType(field.type)
          return `  ${this.renderFieldName(field, false)}${
            isOptional ? '?' : ''
          }: ${this.renderInputFieldType(field.type)},`
        })
        .join('\n')

      let interfaces: GraphQLInterfaceType[] = []
      if (type instanceof GraphQLObjectType) {
        interfaces = (type as any).getInterfaces()
      }

      return this.renderInterfaceWrapper(
        type.name,
        type.description!,
        interfaces,
        fieldDefinition,
      )
    },

    GraphQLScalarType: (type: GraphQLScalarType): string => {
      if (type.name === 'ID') {
        return this.graphqlRenderers.GraphQLIDType(type)
      }
      return `${
        type.description
          ? `/*
${type.description}
*/
`
          : ''
      }export type ${type.name} = ${this.scalarMapping[type.name] || 'string'}`
    },

    GraphQLIDType: (type: GraphQLScalarType): string => {
      return `${
        type.description
          ? `/*
${type.description}
*/
`
          : ''
      }export type ${type.name}_Input = ${this.scalarMapping[type.name] ||
        'string'}
export type ${type.name}_Output = string`
    },

    GraphQLEnumType: (type: GraphQLEnumType): string => {
      return `${this.renderDescription(type.description!)}export type ${
        type.name
      } = ${type
        .getValues()
        .map(e => `  '${e.name}'`)
        .join(' |\n')}`
    },
  }
  constructor({ schema }: { schema: GraphQLSchema }) {
    super({ schema })
  }
  render(options?: RenderOptions) {
    return this.compile`\
/**
 * @flow
 */
${this.renderImports()}

export interface Exists {\n${this.renderExists()}\n}

export interface Node {}

interface Prisma {
  $exists: Exists;
  $request(query: string, variables?: {[key: string]: any}): Promise<any>;
  $delegate: Delegate;
  $getAbstractResolvers(filterSchema?: GraphQLSchema | string): IResolvers;

  /**
   * Queries
  */

${this.renderQueries()};

  /**
   * Mutations
  */

${this.renderMutations()};
}

export interface Delegate {
  (
    operation: 'query' | 'mutation',
    fieldName: string,
    args: {
      [key: string]: any
    },
    infoOrQuery?: GraphQLResolveInfo,
    options?: Options,
  ): Promise<any>,
  query: DelegateQuery,
  mutation: DelegateMutation
}

export interface DelegateQuery {\n${this.renderDelegateQueries()}\n}

export interface DelegateMutation {\n${this.renderDelegateMutations()}\n}

export interface BindingConstructor<T> {
  new(options?: BPOType): T
}

/**
 * Types
*/

${this.renderTypes()}

/**
 * Type Defs
*/

${this.renderTypedefs()}

${this.renderExports(options)}
`
  }
  renderImports() {
    return `\
import type { GraphQLResolveInfo, GraphQLSchema } from 'graphql'
import type { IResolvers } from 'graphql-tools'
import type { BasePrismaOptions as BPOType, Options } from 'prisma-lib'
import { makePrismaBindingClass} from 'prisma-lib'`
  }
  renderPrismaClassArgs(options?: RenderOptions) {
    let endpointString = ''
    let secretString = ''
    if (options) {
      if (options.endpoint) {
        endpointString = options.endpoint
          ? `, endpoint: ${options.endpoint}`
          : ''
      }
      if (options.secret) {
        secretString = options.secret ? `, secret: ${options.secret}` : ''
      }
    }

    return `{typeDefs${endpointString}${secretString}}`
  }
  renderExports(options?: RenderOptions) {
    const args = this.renderPrismaClassArgs(options)
    return `const prisma: BindingConstructor<Prisma> = makePrismaBindingClass(${args})
    export { prisma as Prisma } 
    `
  }
  renderTypedefs() {
    return (
      'const typeDefs = `' + printSchema(this.schema).replace(/`/g, '\\`') + '`'
    )
  }
  renderExists() {
    const queryType = this.schema.getQueryType()
    if (queryType) {
      return `${getExistsFlowTypes(queryType)}`
    }
    return ''
  }
  renderQueries() {
    const queryType = this.schema.getQueryType()
    if (!queryType) {
      return ''
    }
    return this.renderMainMethodFields('query', queryType.getFields(), false)
  }
  renderMutations() {
    const mutationType = this.schema.getMutationType()
    if (!mutationType) {
      return '{}'
    }
    return this.renderMainMethodFields(
      'mutation',
      mutationType.getFields(),
      false,
      true,
    )
  }
  renderDelegateQueries() {
    const queryType = this.schema.getQueryType()
    if (!queryType) {
      return '{}'
    }
    return this.renderMainMethodFields('query', queryType.getFields(), true)
  }
  renderDelegateMutations() {
    const mutationType = this.schema.getMutationType()
    if (!mutationType) {
      return '{}'
    }
    return this.renderMainMethodFields(
      'mutation',
      mutationType.getFields(),
      true,
    )
  }
  renderSubscriptions() {
    const subscriptionType = this.schema.getSubscriptionType()
    if (!subscriptionType) {
      return '{}'
    }
    return this.renderMainMethodFields(
      'subscription',
      subscriptionType.getFields(),
      true,
    )
  }
  getTypeNames() {
    const ast = this.schema
    // Create types
    return Object.keys(ast.getTypeMap())
      .filter(typeName => !typeName.startsWith('__'))
      .filter(typeName => typeName !== (ast.getQueryType() as any).name)
      .filter(
        typeName =>
          ast.getMutationType()
            ? typeName !== (ast.getMutationType()! as any).name
            : true,
      )
      .filter(
        typeName =>
          ast.getSubscriptionType()
            ? typeName !== (ast.getSubscriptionType()! as any).name
            : true,
      )
      .sort(
        (a, b) =>
          (ast.getType(a) as any).constructor.name <
          (ast.getType(b) as any).constructor.name
            ? -1
            : 1,
      )
  }
  renderTypes() {
    const typeNames = this.getTypeNames()
    return typeNames
      .map(typeName => {
        const type = this.schema.getTypeMap()[typeName]
        return this.graphqlRenderers[type.constructor.name]
          ? this.graphqlRenderers[type.constructor.name](type)
          : null
      })
      .join('\n\n')
  }

  renderArgs(
    field: GraphQLField<any, any>,
    renderInfo = false,
    isMutation = false,
    isTopLevel = false,
  ) {
    const { args } = field
    const hasArgs = args.length > 0

    const allOptional = args.reduce((acc, curr) => {
      if (!acc) {
        return false
      }

      return !isNonNullType(curr.type)
    }, true)

    const infoString = renderInfo
      ? ', info?: GraphQLResolveInfo, options?: Options'
      : ''

    // hard-coded for Prisma ease-of-use
    if (isMutation && field.name.startsWith('create')) {
      return `data${allOptional ? '?' : ''}: ${this.renderInputFieldTypeHelper(
        args[0],
        isMutation,
      )}`
    } else if (
      (isMutation && field.name.startsWith('delete')) || // either it's a delete mutation
      (!isMutation &&
        isTopLevel &&
        args.length === 1 &&
        (isObjectType(field.type) || isObjectType((field.type as any).ofType))) // or a top-level single query
    ) {
      return `where${allOptional ? '?' : ''}: ${this.renderInputFieldTypeHelper(
        args[0],
        isMutation,
      )}`
    }

    return `args${allOptional ? '?' : ''}: {${hasArgs ? ' ' : ''}${args
      .map(
        a =>
          `${this.renderFieldName(a, false)}${
            isNonNullType(a.type) ? '' : '?'
          }: ${this.renderInputFieldTypeHelper(a, isMutation)}`,
      )
      .join(', ')}${args.length > 0 ? ' ' : ''}${infoString}}`
  }

  renderInputFieldTypeHelper(field, isMutation) {
    return this.renderFieldType({
      field,
      node: false,
      input: true,
      partial: false,
      renderFunction: false,
      isMutation,
    })
  }

  renderMainMethodFields(
    operation: string,
    fields: GraphQLFieldMap<any, any>,
    delegate = false,
    isMutation = false,
  ): string {
    return Object.keys(fields)
      .map(f => {
        const field = fields[f]
        const T = delegate
          ? `<T>`
          : ''
        return `    ${field.name}: ${T}(${this.renderArgs(
          field,
          delegate,
          isMutation,
          true,
        )}) => ${
          operation === 'subscription'
            ? 'Promise<AsyncIterator<T>>'
            : delegate
              ? 'T'
              : this.renderFieldType({
                  field,
                  node: delegate,
                  input: false,
                  partial: delegate,
                  renderFunction: false,
                  isMutation,
                })
        }`
      })
      .join(';\n')
  }

  getDeepType(type) {
    if (type.ofType) {
      return this.getDeepType(type.ofType)
    }

    return type
  }

  getInternalTypeName(type) {
    const deepType = this.getDeepType(type)
    // if (isListType(type)) {
    //   return `${deepType}Array`
    // }
    const name = String(deepType)
    return name === 'ID' ? 'ID_Output' : name
  }

  getPayloadType(operation: string) {
    if (operation === 'subscription') {
      return `Promise<AsyncIterator<T>>`
    }
    return `Promise<T>`
  }

  renderInterfaceOrObject(
    type: GraphQLObjectTypeRef | GraphQLInputObjectType | GraphQLInterfaceType,
    node = true,
  ): string {
    const fields = type.getFields()
    const fieldDefinition = Object.keys(fields)
      .filter(f => {
        const deepType = this.getDeepType(fields[f].type)
        return node ? !isObjectType(deepType) : true
      })
      .map(f => {
        const field = fields[f]
        return `  ${this.renderFieldName(field, node)}: ${this.renderFieldType({
          field,
          node,
          input: false,
          partial: false,
          renderFunction: true,
          isMutation: false,
        })},`
      })
      .join('\n')

    let interfaces: GraphQLInterfaceType[] = []
    if (type instanceof GraphQLObjectType) {
      interfaces = (type as any).getInterfaces()
    }

    return this.renderInterfaceWrapper(
      `${type.name}${node ? 'Node' : ``}`,
      type.description!,
      interfaces,
      fieldDefinition,
      !node,
    )
  }

  renderFieldName(
    field: GraphQLInputField | GraphQLField<any, any>,
    node: boolean,
  ) {
    if (!node) {
      return `${field.name}`
    }
    return `${field.name}${isNonNullType(field.type) ? '' : '?'}`
  }

  renderFieldType({
    field,
    node,
    input,
    partial,
    renderFunction,
    isMutation = false,
  }: {
    field
    node: boolean
    input: boolean
    partial: boolean
    renderFunction: boolean
    isMutation: boolean
    // node: boolean = true,
    // input: boolean = false,
    // partial: boolean = false,
    // renderFunction: boolean = true,
  }) {
    const { type } = field
    const deepType = this.getDeepType(type)
    const isList = isListType(type) || isListType(type.ofType)
    const isOptional = !(isNonNullType(type) || isNonNullType(type.ofType))
    const isScalar = isScalarType(deepType) || isEnumType(deepType)
    const isInput = field.astNode.kind === 'InputValueDefinition'
    // const isObject = isObjectType(deepType)

    let typeString = this.getInternalTypeName(type)

    if ((node || isList) && !isScalar) {
      typeString += `Node`
    }

    if (isScalar && !isInput) {
      if (isList) {
        typeString += `[]`
      }
      if (node) {
        return typeString
      } else {
        return `(${
          field.args && field.args.length > 0
            ? this.renderArgs(field, isMutation)
            : ''
        }) => Promise<${typeString}>`
      }
    }

    if ((isList || node) && isOptional) {
      typeString += ' | null'
    }

    if (isList) {
      if (isScalar) {
        return `${typeString}[]`
      } else {
        if (renderFunction) {
          return `(${
            field.args && field.args.length > 0
              ? this.renderArgs(field, isMutation)
              : ''
          }) => Promise<Array<${typeString}>>`
        } else {
          return `Promise<Array<${typeString}>>`
        }
      }
    }

    if (partial) {
      typeString = `Partial<${typeString}>`
    }

    if (node && (!isInput || isScalar)) {
      return `Promise<${typeString}>`
    }

    if (isInput || !renderFunction) {
      return typeString
    }

    if (node && !typeString.endsWith('Node')) {
      typeString = `${typeString}Node`
    }
    return `(${
      field.args && field.args.length > 0
        ? this.renderArgs(field, isMutation)
        : ''
    }) => ${typeString}`
  }

  renderInputFieldType(type: GraphQLInputType | GraphQLOutputType) {
    if (isNonNullType(type)) {
      return this.renderInputFieldType((type as GraphQLWrappingType).ofType)
    }
    if (isListType(type)) {
      const inputType = this.renderInputFieldType(
        (type as GraphQLWrappingType).ofType,
      )
      return `${inputType}[] | ${inputType}`
    }
    return `${(type as GraphQLNamedType).name}${
      (type as GraphQLNamedType).name === 'ID' ? '_Input' : ''
    }`
  }

  renderTypeWrapper(
    typeName: string,
    typeDescription: string | void,
    fieldDefinition: string,
  ): string {
    return `${this.renderDescription(
      typeDescription,
    )}export type ${typeName} = {
${fieldDefinition}
}`
  }

  renderInterfaceWrapper(
    typeName: string,
    typeDescription: string | void,
    interfaces: GraphQLInterfaceType[],
    fieldDefinition: string,
    promise?: boolean,
  ): string {
    const actualInterfaces = promise
      ? [
          {
            name: `Promise<${typeName}Node>`,
          },
        ].concat(interfaces)
      : interfaces

    return `${this.renderDescription(
      typeDescription,
    )}export interface ${typeName}${
      actualInterfaces.length > 0
        ? ` extends ${actualInterfaces.map(i => i.name).join(', ')}`
        : ''
    } {
${fieldDefinition}
}`
  }

  renderDescription(description?: string | void) {
    return `${
      description
        ? `/*
${description.split('\n').map(l => ` * ${l}\n`)}
 */
`
        : ''
    }`
  }
}
