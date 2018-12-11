export interface RC {
  platformToken?: string
  clusters?: Clusters
}

export interface Clusters {
  [name: string]: ClusterConfig
}

export interface ClusterConfig {
  host: string
  clusterSecret: string
}

export interface InternalRC {
  platformToken?: string
  clusters?: Clusters
  targets?: InternalTargets
}

export interface InternalTargets {
  [name: string]: string
}
