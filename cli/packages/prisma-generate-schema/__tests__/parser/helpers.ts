import { IGQLType } from '../../src/datamodel/model'

/**
 * Assertion helper for fields.
 */
export function expectField(
  candidate: IGQLType,
  name: string,
  required: boolean,
  list: boolean,
  type: string | IGQLType,
  isId: boolean = false,
  isReadOnly: boolean = false,
  defaultValue: any = null
) {
  const [fieldObj] = candidate.fields.filter(f => f.name === name)

  expect(fieldObj).toBeDefined()

  expect(fieldObj.isRequired).toEqual(required)
  expect(fieldObj.isList).toEqual(list)
  expect(fieldObj.type).toEqual(type)
  expect(fieldObj.defaultValue).toEqual(defaultValue)
  expect(fieldObj.isId).toEqual(isId)
  expect(fieldObj.isReadOnly).toEqual(isReadOnly)
  expect(fieldObj.defaultValue).toEqual(defaultValue)

  return fieldObj
}

/**
 * Assertion helper for types
 */
export function expectType(types: IGQLType[], name: string, isEnum: boolean = false, isEmbedded: boolean = false) {
  const [type] = types.filter(t => t.name === name)

  expect(type).toBeDefined()
  expect(type.isEnum).toEqual(isEnum)
  expect(type.isEmbedded).toEqual(isEmbedded)

  return type
}