import { Command, flags, Flags, ProjectDefinition } from 'graphcool-cli-engine'
import * as chalk from 'chalk'
import { EnvAlreadyExistsError } from '../../errors/EnvAlreadyExistsError'
import { InvalidProjectNameError } from '../../errors/InvalidProjectNameError'
import * as sillyName from 'sillyname'
import * as figures from 'figures'
import { defaultDefinition, examples } from '../../examples'

export default class Init extends Command {
  static topic = 'init'
  static description = 'Create a new project definition and environment from scratch or based on an existing Graphcool definition.'
  static help = `
  
  ${chalk.blue('Examples:')}
  
  ${chalk.gray('-')} Initialize a new Graphcool project
    ${chalk.blue('$ graphcool init')}
  `
  static flags: Flags = {
    copy: flags.string({
      char: 'c',
      description: 'ID or alias of the project to be copied',
    }),
    name: flags.string({
      char: 'n',
      description: 'Project name',
    }),
    alias: flags.string({
      char: 'a',
      description: 'Project alias',
    }),
    env: flags.string({
      char: 'e',
      description: 'Local environment name for the new project',
    }),
    region: flags.string({
      char: 'r',
      description:
        'AWS Region of the project (options: US_WEST_2 (default), EU_WEST_1, AP_NORTHEAST_1)',
    }),
    template: flags.string({
      char: 't',
      description:
        'The template to base the init on. (options: blank, instagram)',
    }),
  }

  async run() {
    const { copy, alias, env, region, template } = this.flags
    let { name } = this.flags
    await this.definition.load()
    this.auth.setAuthTrigger('init')
    await this.auth.ensureAuth()

    const newProject = !this.definition.definition

    if (template) {
      const projectDefinition = examples[template]
      if (!projectDefinition) {
        this.out.error(`${template} is not a valid template`)
      }
      this.definition.set(projectDefinition)
    }

    if (copy) {
      const info = await this.client.fetchProjectInfo(copy)
      this.definition.set(info.projectDefinition)
    }

    if (!this.definition.definition) {
      const newDefinition = await this.interactiveInit()
      this.definition.set(newDefinition)
    }

    if (env && this.env.env.environments[env]) {
      this.out.error(
        new EnvAlreadyExistsError(Object.keys(this.env.env.environments)),
      )
    }

    // create new
    if (this.env.default && !env) {
      this.out.error(
        new EnvAlreadyExistsError(Object.keys(this.env.env.environments)),
      )
    }

    if (name && !isValidProjectName(name)) {
      this.out.error(new InvalidProjectNameError(name))
    }

    name = name || sillyName()

    this.out.action.start(`Creating project ${chalk.bold(name)}`)

    try {
      // create project
      const createdProject = await this.client.createProject(
        name,
        this.definition.definition!,
        alias,
        region,
      )

      // add environment
      const newEnv = env || 'dev'
      await this.env.set(newEnv, createdProject.id)

      if (newProject) {
        this.env.setDefault(newEnv)
        this.out.log('\n')
        this.definition.save(undefined, false)
        this.out.log('\n')
      }
      this.env.save()

      this.out.action.stop(
        `${chalk.green(figures.tick)} Created project ${chalk.bold(
          name,
        )} (ID: ${createdProject.id}) successfully.`,
      )

      this.out.log(`\
   ${chalk.bold('Here is what you can do next:')}

   1) Open ${chalk.bold('graphcool.yml')} or ${chalk.bold(
        'types.graphql',
      )} in your editor to update your project definition.
      You can deploy your changes using ${chalk.cyan('`graphcool deploy`')}.

   2) Use one of the following endpoints to connect to your GraphQL API:

        Simple API:   https://api.graph.cool/simple/v1/${createdProject.id}
        Relay API:    https://api.graph.cool/relay/v1/${createdProject.id}
`)
    } catch (e) {
      this.out.action.stop()
      this.out.error(e)
    }
  }

  async interactiveInit(): Promise<ProjectDefinition> {
    const initQuestion = {
      name: 'init',
      type: 'list',
      message: 'How do you want to start?\n',
      choices: [
        {
          value: 'blank',
          name: [
            `${chalk.bold('New blank project')}`,
            `Creates a new Graphcool project from scratch.`,
            '',
          ].join('\n'),
        },
        {
          value: 'copy',
          name: [
            `${chalk.bold('Copying an existing project')}`,
            `Copies a project from your account`,
            '',
          ].join('\n'),
        },
        {
          value: 'example',
          name: [
            `${chalk.bold('Based on example')}`,
            `Creates a new Graphcool project based on an example`,
            '',
          ].join('\n'),
        },
      ],
      pageSize: 12,
    }

    const { init } = await this.out.prompt([initQuestion])

    switch (init) {
      case 'blank':
        return defaultDefinition
      case 'copy':
        const projectId = await this.projectSelection()
        const info = await this.client.fetchProjectInfo(projectId)
        return info.projectDefinition
      case 'example':
        return await this.exampleSelection()
    }

    return null as any
  }

  private async projectSelection() {
    const projects = await this.client.fetchProjects()
    const choices = projects.map(p => ({
      name: `${p.name} (${p.id})`,
      value: p.id,
    }))

    const question = {
      name: 'project',
      type: 'list',
      message: 'Please choose a project',
      choices,
    }

    const { project } = await this.out.prompt([question])

    return project
  }

  private async exampleSelection(): Promise<ProjectDefinition> {
    const question = {
      name: 'example',
      type: 'list',
      message: 'Please choose an example',
      choices: [
        {
          value: 'instagram',
          name: [
            `${chalk.bold('Instagram')}`,
            `Contains an instagram clone with permission logic`,
            '',
          ].join('\n'),
        },
        {
          value: 'stripe',
          name: [
            `${chalk.bold('Stripe Checkout')}`,
            `An example integrating the stripe checkout with schema extensions`,
            '',
          ].join('\n'),
        },
        {
          value: 'sendgrid',
          name: [
            `${chalk.bold('Sendgrid Mails')}`,
            `An example that shows how to connect Graphcool to the Sendgrid API`,
            '',
          ].join('\n'),
        },
      ],
      pageSize: 12,
    }

    const { example } = await this.out.prompt(question)

    return examples[example]
  }
}

export function isValidProjectName(projectName: string): boolean {
  return /^[A-Z][a-zA-Z0-9\s]*$/.test(projectName)
}
