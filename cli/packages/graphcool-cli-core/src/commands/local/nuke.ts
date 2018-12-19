import { Command, flags, Flags } from 'graphcool-cli-engine'
import Docker from './Docker'

export default class Nuke extends Command {
  static topic = 'local'
  static command = 'nuke'
  static description = 'Hard-reset local development cluster'
  static group = 'local'
  static flags: Flags = {
    name: flags.string({
      char: 'n',
      description: 'Name of the cluster instance',
      defaultValue: 'local',
    }),
  }
  async run() {
    const docker = new Docker(this.out, this.config, this.env, this.flags.name)
    await docker.nuke()
  }
}
