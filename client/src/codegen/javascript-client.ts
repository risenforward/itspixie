import { TypescriptGenerator, RenderOptions } from './typescript-client'
import * as prettier from 'prettier'

export class JavascriptGenerator extends TypescriptGenerator {
  constructor(options) {
    super(options)
  }
  format(code: string, options: prettier.Options = {}) {
    return prettier.format(code, {
      ...options,
      parser: 'babylon',
    })
  }
  renderJavascript(options?: RenderOptions) {
    const args = this.renderPrismaClassArgs(options)
    return this.format(`\
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var prisma_lib_1 = require("prisma-client-lib");
var typeDefs = require("./prisma-schema").typeDefs

exports.Prisma = prisma_lib_1.makePrismaClientClass(${args});
exports.prisma = new exports.Prisma();
${this.renderModels()}
`)
  }
  renderModels() {
    const models = this.internalTypes
      .map(
        i => `{
    name: '${i.name}',
    embedded: ${i.isEmbedded}
  }`,
      )
      .join(',\n')

    return `exports.models = [${models}]`
  }
}
