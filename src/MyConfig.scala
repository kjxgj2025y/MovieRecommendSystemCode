import com.mongodb.casbah.Imports.{MongoClient, MongoClientURI}
import org.apache.spark.sql.{DataFrame, SparkSession}

case class MongoConfig(uri: String, db: String)

object MyConfig {
  // MongoDB连接常量
  val config = Map(
    "mongo.uri" -> "mongodb://master:27017/recommender",
    "mongo.db" -> "recommender"
  )

  // 使用SparkSession构建Spark应用程序
  val spark: SparkSession = SparkSession.builder()
    .master("local[*]")  // Spark 应用程序连接的集群管理器的地址
    .config("spark.executor.memory", "8g")  // 为每个执行器设置内存量
    .config("spark.executor.cores", "4")  // 为每个执行器设置CPU核心量
    .appName("MovieRecommender").getOrCreate

  // 建一个mongodb的连接
  implicit val mongoConfig: MongoConfig = MongoConfig(config("mongo.uri"), config("mongo.db"))
  val mongoClient = MongoClient(MongoClientURI(mongoConfig.uri))

  // 定义一个读mongodb集合函数
  def ReadMongoDB(spark: SparkSession, collection_name: String)(implicit mongoConfig: MongoConfig): DataFrame = {
    spark.read
      .option("uri", mongoConfig.uri)
      .option("collection", collection_name)
      .format("com.mongodb.spark.sql")
      .load()
      .drop("_id")
  }

  // 定义一个写mongodb集合函数
  def WriteMongoDB(df: DataFrame, collection_name: String)(implicit mongoConfig: MongoConfig): Unit = {
    df.write
      .option("uri", mongoConfig.uri)
      .option("collection", collection_name)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()
  }
}
