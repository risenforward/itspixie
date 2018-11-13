## Scripts to re-generate unit tests

These scripts re-generate the ground-truth schema for all unit tests from an existing prisma service. 

### Workflow 

Workfow for re-generating tests using these scripts. Please make sure you are in the correct working directory when executing them. 

0) Start in this directory.
1) `cd mongodb` or `cd mysql`.
2) (optional) docker-compose up (if you want to run this locally).
3) Set the `PRISMA_HOST` environment variable to your prisma host.
3) Execute `../deploySchemas.sh`, which will deploy all test models to `https://$PRISMA_HOST:4466/schema-generator/MODEL`.
4) `cd ..`
5) `./fetschSchemas NAME`, where name is usually `relational` or `document`, depending on which DB is running on your `PRISMA_HOST`. 
6) If you used docker, you can stop your containers again.