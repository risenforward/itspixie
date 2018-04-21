// import * as nock from 'nock'
import { mocki } from './inquirer-mock-prompt'
import { Config } from 'prisma-cli-engine'
import Init from './'
import { writeToStdIn, DOWN, ENTER } from '../../test/writeToStdin'
import { getTmpDir } from '../../test/getTmpDir'
import * as fs from 'fs-extra'
import * as path from 'path'
import * as cuid from 'scuid'

function makeMockConfig(mockInquirer?: any) {
  const home = getTmpDir()
  const cwd = path.join(home, cuid())
  fs.mkdirpSync(cwd)
  return {
    config: new Config({ mock: true, home, cwd, mockInquirer }),
    home,
    cwd,
  }
}

function getFolderContent(folder) {
  return fs
    .readdirSync(folder)
    .map(f => ({ [f]: fs.readFileSync(f, 'utf-8') }))
    .reduce((acc, curr) => ({ ...acc, ...curr }), {})
}

async function testChoices(choices) {
  const mockInquirer = mocki(choices)
  const { config, home, cwd } = makeMockConfig(mockInquirer)
  const result = await Init.mock({ mockConfig: config })
  expect(getFolderContent(cwd)).toMatchSnapshot()
  expect(result.out.stdout.output).toMatchSnapshot()
}

describe('init', () => {
  test('choose local', async () => {
    await testChoices({
      choice: 'local',
    })
  })

  test('test project', async () => {
    await testChoices({
      choice: 'Use existing database',
      dbType: 'postgres',
      host: 'localhost',
      port: 5432,
      user: 'postgres',
      password: '',
      database: undefined,
      alreadyData: false,
    })
  })
})
