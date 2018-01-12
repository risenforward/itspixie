import {
  Command,
  Flags,
  flags,
  ProjectInfo,
  Output,
  Project,
} from 'graphcool-cli-engine'
import chalk from 'chalk'
import { Cluster } from 'graphcool-yml'

export interface Service {
  project: {
    name: string
    stage: string
  }
  cluster: Cluster
}

export default class InfoCommand extends Command {
  static topic = 'info'
  static description = 'Display service information (endpoints, cluster, ...)'
  static group = 'general'
  static flags: Flags = {
    json: flags.boolean({
      char: 'j',
      description: 'Json Output',
    }),
    current: flags.boolean({
      char: 'c',
      description: 'Only show info for current service',
    }),
    secret: flags.boolean({
      char: 's',
      description: 'Print secret in json output',
    }),
    ['env-file']: flags.string({
      description: 'Path to .env file to inject env vars',
      char: 'e',
    }),
  }
  async run() {
    const { json, secret } = this.flags
    const envFile = this.flags['env-file']
    await this.definition.load(this.flags, envFile)
    const serviceName = this.definition.definition!.service
    const stage = this.definition.definition!.stage
    const workspace = this.definition.getWorkspace()

    // if (current) {
    const clusterName = this.definition.getClusterName()
    if (!clusterName) {
      throw new Error(
        `No cluster set. Please set the "cluster" property in your graphcool.yml`,
      )
    }
    const cluster = this.definition.getCluster()
    if (!cluster) {
      throw new Error(`Cluster ${clusterName} could not be found in global ~/.graphcoolrc.
Please make sure it contains the cluster. You can create a local cluster using 'gc local start'`)
    }
    if (!json) {
      this.out.log(`Service Name: ${chalk.bold(serviceName)}`)
    }
    this.out.log(
      this.printStage(
        serviceName,
        stage,
        cluster,
        this.definition.secrets,
        workspace || undefined,
        json,
      ),
    )
  }

  printStage(
    name: string,
    stage: string,
    cluster: Cluster,
    secrets: string[] | null,
    workspace?: string,
    printJson: boolean = false,
  ) {
    const { secret } = this.flags
    if (printJson) {
      const result: any = {
        name,
        stage,
        cluster: cluster.name,
        workspace,
        httpEndpoint: cluster.getApiEndpoint(name, stage, workspace),
        wsEndpoint: cluster.getWSEndpoint(name, stage, workspace),
      }

      if (secret) {
        result.secret = secrets
      }
      return JSON.stringify(result, null, 2)
    }
    return `
  ${chalk.bold(stage)} (cluster: ${chalk.bold(`\`${cluster.name}\``)})

    HTTP:       ${cluster.getApiEndpoint(name, stage, workspace)}
    Websocket:  ${cluster.getWSEndpoint(name, stage, workspace)}`
  }
}
