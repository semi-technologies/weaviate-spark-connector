package io.weaviate.spark

import org.apache.spark.sql.connector.catalog.{Table, TableProvider}
import org.apache.spark.sql.connector.expressions.Transform
import org.apache.spark.sql.sources.DataSourceRegister
import org.apache.spark.sql.types.{ArrayType, DataType, DataTypes, Metadata, StructField, StructType}
import org.apache.spark.sql.util.CaseInsensitiveStringMap
import technology.semi.weaviate.client.Config
import technology.semi.weaviate.client.WeaviateClient
import technology.semi.weaviate.client.v1.schema.model.Property

import java.util
import scala.jdk.CollectionConverters._

class WeaviateResultError(s: String) extends Exception(s) {}
class WeaviateClassNotFoundError(s: String) extends Exception(s) {}

class Weaviate extends TableProvider with DataSourceRegister {
  override def shortName(): String = "weaviate"
  override def inferSchema(options: CaseInsensitiveStringMap): StructType = {
    val host = options.get("host")
    val scheme = options.get("scheme")
    val config = new Config(scheme, host)
    val client = new WeaviateClient(config)
    val className = options.get("className")
    val result = client.schema.classGetter.withClassName(className).run
    if (result.hasErrors) throw new WeaviateResultError(result.getError.getMessages.toString)
    if (result.getResult == null) throw new WeaviateClassNotFoundError("Class "+className+ " was not found.")
    val properties = result.getResult.getProperties.asScala
    // TODO p.getDataType returns List<string> so need to think about how to convert to Spark datatype
    val structFields = properties.map(p => convertWeaviateTypesToStructField(p))
    new StructType(structFields.toArray)
  }

  def convertWeaviateTypesToStructField(property: Property): StructField = {
    val dataType: DataType = convertWeaviateDatatypeToSparkDataType(property.getDataType)
    StructField(property.getName(), dataType, true, Metadata.empty)
  }

  def convertWeaviateDatatypeToSparkDataType(weaviateDataType: util.List[String]): DataType = {
    weaviateDataType.get(0) match {
      case "text" | "string" | "geoCoordinates" | "phoneNumber" | "blob" => DataTypes.StringType
      case "text[]" | "string[]" => ArrayType(DataTypes.StringType)
      case "int" => DataTypes.IntegerType
      case "int[]" => ArrayType(DataTypes.IntegerType)
      case "number" => DataTypes.FloatType
      case "number[]" => ArrayType(DataTypes.FloatType)
      case "boolean" => DataTypes.BooleanType
      case "boolean[]" => ArrayType(DataTypes.BooleanType)
      // Weaviate dates need to be timestamped so use TimestampType Spark DataType
      case "date" => DataTypes.TimestampType
      case "date[]" => ArrayType(DataTypes.TimestampType)
    }
  }

  override def getTable(schema: StructType, partitioning: Array[Transform], properties: util.Map[String, String]): Table =
    WeaviateCluster()
}
