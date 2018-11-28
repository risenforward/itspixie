import * as ts from 'typescript'
import * as path from 'path'
import * as fs from 'fs'
import { buildSchema } from 'graphql'
import { TypescriptGenerator } from '../typescript-client'
import { FlowGenerator } from '../flow-client'
import { execFile } from 'child_process'
const flow = require('flow-bin')

function compile(fileNames: string[], options: ts.CompilerOptions): number {
  const program = ts.createProgram(fileNames, options)
  const emitResult = program.emit()

  const allDiagnostics = ts
    .getPreEmitDiagnostics(program)
    .concat(emitResult.diagnostics)

  allDiagnostics.forEach(diagnostic => {
    if (diagnostic.file) {
      const { line, character } = diagnostic.file.getLineAndCharacterOfPosition(
        diagnostic.start!
      )
      const message = ts.flattenDiagnosticMessageText(
        diagnostic.messageText,
        "\n"
      )
      console.log(
        `${diagnostic.file.fileName} (${line + 1},${character + 1}): ${message}`
      )
    } else {
      console.log(
        `${ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n")}`
      )
    }
  })

  return emitResult.emitSkipped ? 1 : 0
}

export async function testTSCompilation(typeDefs) {
  const schema = buildSchema(typeDefs)
  const generator = new TypescriptGenerator({
    schema,
    internalTypes: [],
  })

  const file = generator.render().toString()
  const artifactsPath = path.join(__dirname, '..', 'artifacts')

  if (!fs.existsSync(artifactsPath)) {
    fs.mkdirSync(artifactsPath);
  }

  const filePath = path.join(__dirname, '..', 'artifacts', 'generated_ts.ts')
  await fs.writeFileSync(filePath, file)

  // TODO: Remove ugly way to ignore TS import error
  const func = `export const typeDefs = ''`
  await fs.writeFileSync(path.join(artifactsPath, 'prisma-schema.ts'), func)

  return compile([filePath], {
    noEmitOnError: true,
    noImplicitAny: true,
    skipLibCheck: true,
    target: ts.ScriptTarget.ESNext,
    module: ts.ModuleKind.CommonJS
  })
}

export async function testFlowCompilation(typeDefs) {
  const schema = buildSchema(typeDefs)
  const generator = new FlowGenerator({
    schema,
    internalTypes: [],
  })

  const file = generator.render().toString()
  const artifactsPath = path.join(__dirname, '..', 'artifacts')

  if (!fs.existsSync(artifactsPath)) {
    fs.mkdirSync(artifactsPath)
  }

  const filePath = path.join(artifactsPath, 'generated_flow.js')
  await fs.writeFileSync(filePath, file)
  
  const flowConfig = ` [ignore]\n [libs]\n [lints]\n [include] ${artifactsPath} \n [strict]`
  const configFilePath = path.join(__dirname, '..', 'artifacts', '.flowconfig')
  await fs.writeFileSync(configFilePath, flowConfig)

  const stdout = await new Promise(resolve => {
    return execFile(
      flow,
      ['check', configFilePath],
      (_err: any, stdout: string) => {
        resolve(stdout)
      },
    )
  })

  return (stdout as string).length
}