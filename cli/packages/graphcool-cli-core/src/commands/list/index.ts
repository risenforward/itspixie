import { Command } from 'graphcool-cli-engine'
import { table, getBorderCharacters } from 'table'
import { NoClusterSetError } from '../../errors/NoClusterSetError'
import { ClusterNotFoundError } from '../../errors/ClusterNotFoundError'
const debug = require('debug')('command')

export interface Project {
  name: string
  stage: string
  cluster: string
}

export default class List extends Command {
  static topic = 'list'
  static description = 'List all deployed services'
  static group = 'general'
  static aliases = ['ls']
  async run() {
    let projects: Project[] = []

    await this.definition.load(this.flags)

    for (const cluster of this.env.clusters.filter(c => c.local)) {
      await this.client.initClusterClient(
        cluster,
        this.definition.getWorkspace() || '*',
        '*',
        '*',
      )
      try {
        this.env.setActiveCluster(cluster)
        debug('listing projects')
        const clusterProjects = await this.client.listProjects()
        const mappedClusterProjects = clusterProjects.map(p => ({
          ...p,
          cluster: cluster.name,
        }))
        projects = [...projects, ...mappedClusterProjects]
      } catch (e) {
        debug(e)
      }
    }

    this.printProjects(projects)
  }

  printProjects(projects: Project[]) {
    this.out.table(projects)
  }
}
