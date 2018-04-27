## Amazon Kinesis Socket Server Sample

Sample Java application that uses the Amazon Kinesis Client Library to read a Kinesis Data Stream and output data records to connected clients over a TCP socket.

This sample application uses the Amazon Kinesis Client Library (KCL) example application described [here](https://docs.aws.amazon.com/streams/latest/dev/kinesis-record-processor-implementation-app-java.html) as a starting point. The application implements the V2 KCL interface.

Data records are sent to connected clients as UTF8 encoded JSON strings.

## Requirements & Prerequisites

#### Maven

This application requires Maven to download dependancies and build. The application can be built by
```
mvn package
```

#### AWS Credentials File

This application uses AWS IAM programatic access credentials stored in a file calls `Credentials` in a directory within user's home directory `~/.aws/`.  The file must contain the following lines.
```
[default]
aws_access_key_id = <your access key id>
aws_secret_access_key = <your secret key>
```

#### IAM Security Policy

The IAM user credentials stored in the Credentials file must have the following permissions.

##### Amazon Kinesis
```
kinesis:GetShardIterator
kinesis:DescribeStream
kinesis:ListTagsForStream
kinesis:GetRecords
```
(specific to Kinesis Data Stream that is being read as the target resource)

#### Amazon DynamoDB

```
dynamodb:BatchGetItem
dynamodb:BatchWriteItem
dynamodb:PutItem
dynamodb:DeleteItem
dynamodb:Scan
dynamodb:Query
dynamodb:UpdateItem
dynamodb:DeleteTable
dynamodb:CreateTable
dynamodb:DescribeTable
dynamodb:GetItem
dynamodb:UpdateTable
dynamodb:GetRecords
```
(specify all tables as the target resource)

## Usage
1. Edit the ServerBootstrap.java class to include the name of your Kinesis Data Stream in the `SAMPLE_APPLICATION_STREAM_NAME` variable

2. Edit the ServerBootstrap.java class to include a name for the application in the the `SAMPLE_APPLICATION_NAME` variable.

3. Build the project
```mvn clean compile exec:java```


## License Summary

This sample code is made available under a modified MIT license. See the LICENSE file.
