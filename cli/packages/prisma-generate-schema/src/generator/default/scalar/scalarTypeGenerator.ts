import { ModelObjectTypeGenerator, RelatedGeneratorArgs, IGenerators, ModelEnumTypeGeneratorBase, ScalarTypeGeneratorBase } from '../../generator'
import { IGQLType, IGQLField } from '../../../datamodel/model'
import GQLAssert from '../../../util/gqlAssert'
import { GraphQLList, GraphQLEnumType, GraphQLType, GraphQLEnumValueConfigMap, GraphQLScalarType, GraphQLString, GraphQLInt, GraphQLFloat, GraphQLBoolean, GraphQLID, GraphQLNonNull } from "graphql/type"
import { GraphQLObjectType } from 'graphql/type/definition';


function createGraphQLScalarType(typeName: string) {
  return new GraphQLScalarType({ name: typeName, serialize: () => null })
}

// Theoretically, we could add properties like
// NOMINAL, ORDINAL, INTERVAL, RATIO and a special type for STRING
// And then generate all filter objects based on that, regardless of the
// actual type. 
export abstract class TypeIdentifiers {
  public static string = 'String'
  public static integer = 'Int'
  public static float = 'Float'
  public static boolean = 'Boolean'
  public static long = 'Long'
  public static dateTime = 'DateTime'
  public static id = 'ID'
  public static uuid = 'UUID'
  public static json = 'Json'
}

const scalarTypes = {}
scalarTypes[TypeIdentifiers.string] = GraphQLString
scalarTypes[TypeIdentifiers.integer] = GraphQLInt
scalarTypes[TypeIdentifiers.long] = createGraphQLScalarType('Long')
scalarTypes[TypeIdentifiers.float] = GraphQLFloat
scalarTypes[TypeIdentifiers.boolean] = GraphQLBoolean
scalarTypes[TypeIdentifiers.dateTime] = createGraphQLScalarType('DateTime')
scalarTypes[TypeIdentifiers.id] = GraphQLID
scalarTypes[TypeIdentifiers.json] = createGraphQLScalarType('Json')
scalarTypes[TypeIdentifiers.uuid] = createGraphQLScalarType('UUID')


// tslint:disable-next-line:max-classes-per-file
export default class ScalarTypeGenerator extends ScalarTypeGeneratorBase {
  public getTypeName(input: string | IGQLType, args: {}) {
    if (typeof (input) === 'string') {
      return input as string
    } else {
      return (input as IGQLType).name
    }
  }
  public isScalarField(field: IGQLField) {
    const type = field.type
    if (typeof (type) === 'string') {
      if (scalarTypes.hasOwnProperty(type as string)) {
        return true
      } else {
        GQLAssert.raise(`${type} is not a scalar type.`)
        return false
      }
    } else {
      return (type as IGQLType).isEnum
    }
  }


  public mapToScalarFieldType(field: IGQLField) {
    const maybeListType = this.mapToScalarFieldTypeForceOptional(field)
    return this.requiredIf(field.isRequired || field.isList, maybeListType)
  }
  public mapToScalarFieldTypeForInput(field: IGQLField) {
    const maybeListType = this.mapToScalarFieldTypeForceOptional(field)
    return this.requiredIf((field.isRequired || field.isList) && field.defaultValue === null, maybeListType)
  }

  public mapToScalarFieldTypeForceRequired(field: IGQLField) {
    const type = this.mapToScalarFieldTypeForceOptional(field)
    return new GraphQLNonNull(type)
  }

  public mapToScalarFieldTypeForceOptional(field: IGQLField) {
    const type = this.generate(field.type as string, {})

    if (field.isList) {
      return this.wrapList(type)
    } else {
      return type
    }
  }

  /**
   * This method intentionally ignores the required flag for lists,
   * as we generate some relation fields without the required flag. 
   * @param field 
   * @param type 
   */
  public wraphWithModifiers<T extends GraphQLType>(field: IGQLField, type: T): T | GraphQLList<GraphQLNonNull<T>> | GraphQLNonNull<T> {
    if (field.isList) {
      return this.wrapList(type)
    } else {
      return this.requiredIf(field.isRequired, type)
    }
  }

  public wrapList<T extends GraphQLType>(type: T): GraphQLList<GraphQLNonNull<T>> {
    return new GraphQLList(
      new GraphQLNonNull(type))
  }

  public requiredIf<T extends GraphQLType>(required: boolean, type: T): GraphQLNonNull<T> | T {
    if (required) {
      return new GraphQLNonNull(type)
    } else {
      return type
    }
  }

  protected generateInternal(input: string | IGQLType, args?: {}) {
    if (typeof (input) === 'string') {
      if (!scalarTypes.hasOwnProperty(input as string)) {
        GQLAssert.raise('Invalid scalar type given: ' + input as string)
      }

      return scalarTypes[input as string]
    } else {
      if (!(input as IGQLType).isEnum) {
        GQLAssert.raise('Not an enum: ' + (input as IGQLType).name)
      }
      return this.generators.modelEnumTypeGenerator.generate(input as IGQLType, {})
    }
  }

}