import MyConfig.{spark, mongoClient, mongoConfig, WriteMongoDB, ReadMongoDB}
import com.mongodb.casbah.Imports.MongoDBObject

object DataWrite {
  // 数据集常量
  val MOVIE_DATA_PATH = "hdfs://master:9000/input/data/Movie.csv"
  val RATING_DATA_PATH = "hdfs://master:9000/input/data/Rating.csv"
  val TAG_DATA_PATH = "hdfs://master:9000/input/data/Tag.csv"

  // 定义MongoDB集合名
  val WRITE_MOVIE_COLLECTION = "Movie"
  val WRITE_RATING_COLLECTION = "Rating"
  val WRITE_TAG_COLLECTION = "Tag"

  def main(args: Array[String]): Unit = {
    // 加载数据
    val movieDF = spark.read.option("header", true).option("delimiter","$").csv(MOVIE_DATA_PATH)
    val ratingDF = spark.read.option("header", true).option("delimiter","$").csv(RATING_DATA_PATH)
    val tagDF = spark.read.option("header", true).option("delimiter","$").csv(TAG_DATA_PATH)

    // 如果mongodb中已经有相应的集合先删除
    mongoClient(mongoConfig.db)(WRITE_MOVIE_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(WRITE_RATING_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(WRITE_TAG_COLLECTION).dropCollection()

    // 将movieDF数据写入对应的mongodb集合中
    WriteMongoDB(movieDF,WRITE_MOVIE_COLLECTION)
    // 将ratingDF数据写入对应的mongodb集合中
    WriteMongoDB(ratingDF,WRITE_RATING_COLLECTION)
    // 将tagDF数据写入对应的mongodb集合中
    WriteMongoDB(tagDF,WRITE_TAG_COLLECTION)

    //对数据表建索引
    mongoClient(mongoConfig.db)(WRITE_MOVIE_COLLECTION).createIndex(MongoDBObject("movie_id" -> 1))
    mongoClient(mongoConfig.db)(WRITE_RATING_COLLECTION).createIndex(MongoDBObject("user_id" -> 1))
    mongoClient(mongoConfig.db)(WRITE_TAG_COLLECTION).createIndex(MongoDBObject("user_id" -> 1))

    // 资源连接
    spark.stop()
  }
}
