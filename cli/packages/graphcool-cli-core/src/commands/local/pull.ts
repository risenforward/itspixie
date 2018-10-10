import { Command, Flags, flags } from 'graphcool-cli-engine'
import Docker from './Docker'

export default class PullLocal extends Command {
  static topic = 'local'
  static command = 'pull'
  static description = 'Pull the latest Graphcool version'
  static flags: Flags = {
    name: flags.string({
      char: 'n',
      description: 'Name of the new instance',
      defaultValue: 'dev'
    }),
  }
  async run() {
    const docker = new Docker(this.out, this.config, this.env, this.flags.name)
    await docker.pull()
  }
}
