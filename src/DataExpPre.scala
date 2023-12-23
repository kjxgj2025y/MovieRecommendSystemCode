import MyConfig.{ReadMongoDB, WriteMongoDB, mongoClient, mongoConfig, spark}
import org.apache.spark.sql.functions.{avg, col, count, regexp_replace}
import com.mongodb.casbah.Imports.MongoDBObject
import org.apache.spark.sql.DataFrame


object DataExpPre {
  // MongoDB集合名(读数据)
  val READ_MOVIE_COLLECTION = "Movie"
  val READ_RATING_COLLECTION = "Rating"
  val READ_TAG_COLLECTION = "Tag"

  // MongoDB集合名(写数据)
  val WRITE_MOVIE_COLLECTION = "MovieExpPre"
  val WRITE_RATING_COLLECTION = "RatingExpPre"
  val WRITE_TAG_COLLECTION = "TagExpPre"

  // 数据探索
  def dataExp(Movie: DataFrame, Rating: DataFrame, Tag: DataFrame): Unit = {
    // 分别统计3个数据集总数
    println("原始Movie数据总数：", Movie.count())
    println("原始Rating数据总数：", Rating.count())
    println("原始Tag数据总数：", Tag.count())
    // 分别对3个数据集统计重复数据量(总数-去重总数)
    println("Movie重复数据总数：", Movie.count()-Movie.dropDuplicates().count())
    println("Rating重复数据总数：", Rating.count()-Rating.dropDuplicates().count())
    println("Tag重复数据总数：", Tag.count()-Tag.dropDuplicates().count())
    println()

    // 分别对3个数据集统计"null"值数量
    // columns.map(col => col.isNull) 返回一个包含了null的DataFrame，使用.reduce(_ || _) 的操作将这些逻辑值逐个聚合为一个总的逻辑值条件，即判断每一列是否都为 null 的条件
    println("Movie null值总数：", Movie.filter(Movie.columns.map(x => col(x).isNull).reduce(_||_)).count())
    println("Rating null值总数：", Rating.filter(Rating.columns.map(x => col(x).isNull).reduce(_||_)).count())
    println("Tag null值总数：", Tag.filter(Tag.columns.map(x => col(x).isNull).reduce(_||_)).count())
    println()

    // 统计带“编剧:”和“主演:”值数据的列(异常数据)
    val anom_count_1 = Movie.select("movie_id","movie_name","movie_writer","movie_starring").filter(Movie.columns.map(x => col(x).contains(":")).reduce(_||_))
    println("Movie带有'编剧:'和'主演:'值数量：", anom_count_1.count())
    // 查看Rating数据集中的user_rating列异常数据
    // Rating的'user_rating'列异常值：如“allstar10 rating”“allstar20 rating”等分别代表“评分为1”“评分为2”以此类推评分是1分到5分，根据这种方式将评分值提取出来。
    val anom_count_2 = Rating.filter(
      col("user_rating").contains("allstar10 rating") ||
        col("user_rating").contains("allstar20 rating") ||
        col("user_rating").contains("allstar30 rating") ||
        col("user_rating").contains("allstar40 rating") ||
        col("user_rating").contains("allstar50 rating")
    )
    println("Rating的'user_rating'列异常值数量：", anom_count_2.count())
    println()

    // 统计电影类型(Movie数据集中的movie_type列)
    val movie_type_count = Tag.select("movie_id","user_tag").distinct()
    println("电影类型：", movie_type_count.select("user_tag").distinct().count() + "种")
    movie_type_count.show(5)

    // 统计Top10电影评分(Movie数据集中的movie_rating列)
    val movie_rating_count = Movie.select("movie_id","movie_name","movie_rating","rating_people_num")
      .orderBy(col("rating_people_num").cast("Int").desc,col("movie_rating").cast("Double").desc)
    println("Top10电影评分：")
    movie_rating_count.show(10)
  }

  // 数据预处理
  def dataPre(Movie: DataFrame, Rating: DataFrame, Tag: DataFrame): Unit = {
    // 分别对3个数据集去除重复数据
    val movie_dup = Movie.dropDuplicates()
    val rating_dup = Rating.dropDuplicates()
    val tag_dup = Tag.dropDuplicates()
    println("Movie去重后总数：", movie_dup.count())
    println("Rating去重后总数：", rating_dup.count())
    println("Tag去重后总数：", tag_dup.count())
    println()

    // 分别对3个数据集去除"null"值数据
    val movie_drop_null_num = movie_dup.filter(movie_dup.columns.map(x => col(x).isNotNull).reduce(_||_))
    val rating_drop_null_num = rating_dup.filter(rating_dup.columns.map(x => col(x).isNotNull).reduce(_||_))
    val tag_drop_null_num = tag_dup.filter(tag_dup.columns.map(x => col(x).isNotNull).reduce(_||_))
    println("去除 Movie null值后总数：", movie_drop_null_num.count())
    println("去除 Rating null值后总数：", rating_drop_null_num.count())
    println("去除 Tag null值后总数：", tag_drop_null_num.count())
    println()

    // 对Movie数据集去除带有多余的异常数据如：“编剧:”和“主演:”数据
    val anom_data_1 = movie_drop_null_num
      .withColumn("movie_writer", regexp_replace(col("movie_writer"),"编剧:",""))
      .withColumn("movie_starring",regexp_replace(col("movie_starring"),"主演:",""))
    println("去除 Movie 异常数据后：")
    anom_data_1.show(5)

    // 对Rating数据集去除带有多余的异常数据如：“allstar10 rating”和“allstar20 rating”等
    val anom_data_2 = rating_drop_null_num.withColumn("user_rating",col("user_rating").substr(8,1).cast("Double"))
    println("去除 Rating 异常数据后：")
    anom_data_2.show(5)

    // 用户评分数据统计(Rating数据集中的user_id列和movie_id列)
    val user_count = anom_data_2.select("user_id").distinct().count()
    val movie_count = anom_data_2.select("movie_id").distinct().count()
    val user_comm_avg = anom_data_2.select("user_id","movie_id")
      .groupBy("user_id").agg(count("movie_id").as("user_comm_count"))
      .select("user_comm_count").agg(avg("user_comm_count").as("user_comm_avg"))
    val movie_comm_avg = anom_data_2.select("user_id","movie_id")
      .groupBy("movie_id").agg(count("user_id").as("movie_comm_count"))
      .select("movie_comm_count").agg(avg("movie_comm_count").as("movie_comm_avg"))
    println(s"总用户数：$user_count")
    println(s"总电影数：$movie_count")
    println("用户评价电影平均次数：")
    user_comm_avg.show(1)
    println("电影被评价平均次数：")
    movie_comm_avg.show(1)

    // 如果mongodb中已经有相应的集合先删除
    mongoClient(mongoConfig.db)(WRITE_MOVIE_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(WRITE_RATING_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(WRITE_TAG_COLLECTION).dropCollection()

    // 将数据预处理完的数据写入对应的mongodb集合中
    WriteMongoDB(anom_data_1, WRITE_MOVIE_COLLECTION)
    WriteMongoDB(anom_data_2, WRITE_RATING_COLLECTION)
    WriteMongoDB(tag_drop_null_num, WRITE_TAG_COLLECTION)

    //对数据表建索引
    mongoClient(mongoConfig.db)(WRITE_MOVIE_COLLECTION).createIndex(MongoDBObject("movie_id" -> 1))
    mongoClient(mongoConfig.db)(WRITE_RATING_COLLECTION).createIndex(MongoDBObject("user_id" -> 1))
    mongoClient(mongoConfig.db)(WRITE_TAG_COLLECTION).createIndex(MongoDBObject("user_id" -> 1))
  }

  def main(args: Array[String]): Unit = {
    // 读取mongodb中的Movie数据
    val movieDF = ReadMongoDB(spark, READ_MOVIE_COLLECTION)
    // 读取mongodb中的rating数据
    val ratingDF = ReadMongoDB(spark, READ_RATING_COLLECTION)
    // 读取mongodb中的tag数据
    val tagDF = ReadMongoDB(spark, READ_TAG_COLLECTION)

    //调用数据探索函数和数据预处理函数
    dataExp(movieDF,ratingDF,tagDF)
    dataPre(movieDF,ratingDF,tagDF)
    // 资源连接
    spark.stop()
  }
}
