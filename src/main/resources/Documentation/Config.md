Configuration
=========================

The kinesis-events plugin is configured by adding a plugin stanza in the
`gerrit.config` file, for example:

```text
[plugin "kinesis-events"]
    numberOfSubscribers = 6
    pollingIntervalMs = 500
    maxRecords = 99
    region = us-east-1
    endpoint = http://localhost:4566
    applicationName = instance-1
    initialPosition = trim_horizon
```

`plugin.kinesis-events.numberOfSubscribers`
:   The number of expected kinesis subscribers. This will be used to allocate
    a thread pool able to run all subscribers.
    Default: 6

`plugin.kinesis-events.pollingIntervalMs`
:   Optional. How often, in milliseconds, to poll Kinesis shards to retrieve
    records. Please note that setting this value too low might incur in
    `ProvisionedThroughputExceededException`.
    See [AWS docs](https://docs.aws.amazon.com/streams/latest/dev/kinesis-low-latency.html)
    for more details on this.
    Default: 1000

`plugin.kinesis-events.maxRecords`
:   Optional. The maximum number of records to fetch from the kinesis stream
    Default: 100

`plugin.kinesis-events.region`
:   Optional. Which AWS region to connect to.
    Default: When not specified this value is provided via the default Region
    Provider Chain, as explained [here](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html)

`plugin.kinesis-events.endpoint`
:   Optional. When defined, it will override the default kinesis endpoint and it
    will connect to it, rather than connecting to AWS. This is useful when
    developing or testing, in order to connect locally.
    See [localstack](https://github.com/localstack/localstack) to understand
    more about how run kinesis stack outside AWS.
    Default: <empty>

`plugin.kinesis-events.applicationName`
:   Optional. This value identifies the application and it must be unique within your
    gerrit cluster to allow different gerrit nodes to have different checkpoints
    and to consume data from the stream independently.
    Default: kinesis-events

`plugin.kinesis-events.initialPosition`
:   Optional. Which point in the stream the consumer should start consuming from.
    Note that this only applies to consumers that *do not* have any persisted
    checkpoint (i.e. first deployments). When checkpoints exist they always
    override this value.

    Needs to be one of these values:

* TRIM_HORIZON: Start streaming at the last untrimmed record in the shard, which is the oldest data record in the shard.
* LATEST: Start streaming just after the most recent record in the shard, so that you always read the most recent data in the shard.

    Default: "LATEST"

`plugin.kinesis-events.publishSingleRequestTimeoutMs`
: Optional. The maximum total time (milliseconds) elapsed between when a publish
  request started and the receiving a response. If it goes over, the request
  will be timed-out and possibly tried again, if `publishTimeoutMs` allows.
  Default: 6000

`plugin.kinesis-events.publishTimeoutMs`
: Optional. The maximum total time (milliseconds) waiting for publishing a record
  to kinesis, including retries.
  If it goes over, the request will be timed-out and not attempted again.
  Default: 6000

`plugin.kinesis-events.shutdownTimeoutMs`
: Optional. The maximum total time (milliseconds) waiting when shutting down
  kinesis consumers.
  Default: 20000

`plugin.kinesis-events.awsLibLogLevel`
: Optional. Which level AWS libraries should log at.
  This plugin delegates most complex tasks associated to the production and
  consumption of data to libraries developed and maintained directly by AWS.
  This configuration specifies how verbose those libraries are allowed to be when
  logging.

  Default: WARN
  Allowed values:OFF|FATAL|ERROR|WARN|INFO|DEBUG|TRACE|ALL

Overrides
=========================

Note that System properties always override and take priority over the above
gerrit.config configuration.