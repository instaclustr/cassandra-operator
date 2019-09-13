## Cassandra Directory Layout (under /etc/cassandra inside Cassandra container)
#### `cassandra.yaml.d/`
Contains YAML fragment files (`.yaml`) that will be loaded by Cassandra on startup in lexicographical order.
These fragments are loaded after the main `cassandra.yaml` file.

These fragments may override existing settings set in the main `cassandra.yaml` file, or settings defined in any previous fragments.

Use cases include:

* Setting default compaction throughput
* Enabling authentication/authorization
* Tuning thread pool sizes

#### `cassandra-env.sh.d/`
Contains bash shell script fragment files (`.sh`) that will be sourced (in lexicographical order) during Cassandra
startup from the main `cassandra.sh` startup script.

These scripts may perform a number of operations, including modifying variables such as `CASSANDRA_CLASSPATH` or
`JVM_OPTS`, though for the latter prefer to use `.options` fragments (see below) unless shell evaluation/expansion is required.

#### `jvm.options.d/`
Contains text fragment files (`.options`) that will be parsed (in lexicographical order) during Cassandra startup
from the main `cassandra.sh` startup script file to construct the JVM command line parameters.

These fragment files are parsed identically to the main `jvm.options` file -- lines not staring with a `-` are ignored.
All other lines are assumed to define command line arguments for the JVM.

Use cases include:
* Tuning JVM CG settings
* Configuring GC logging
