import MyConfig.{ReadMongoDB, WriteMongoDB, mongoClient, mongoConfig, spark}
import org.apache.spark.sql.functions.{col, regexp_replace}

object WebData {
  // 准备后台数据，如添加海报图片地址数据到数据集RateMoreMovies、RateMoreRecentlyMovies和AverageMovies中
  def main(args: Array[String]): Unit = {
    val READ_TAG_COLLECTION = "MovieDF"  // 原始电影信息数据
    val RATE_MORE_MOVIES = "MovieDescDF" // 排序后的电影信息数据
    val READ_RATING_COLLECTION = "RateMoreMovies" // RateMoreMovies(历史热门电影)
    val RATE_MORE_RECENTLY_MOVIES = "RateMoreRecentlyMovies" // RateMoreRecentlyMovies(最近热门电影)
    val AVERAGE_MOVIES = "AverageMovies" // AverageMovies(电影平均得分)

    val PosterDF = spark.read.option("header", true).option("delimiter","$").csv("data/wp/Poster.csv")
    val MovieDF = ReadMongoDB(spark, READ_TAG_COLLECTION)
    val RateMoreMovies = ReadMongoDB(spark, RATE_MORE_MOVIES)

    // 历史热门电影前50部，添加海报图片地址数据
    val RateMoreMoviesPoster = RateMoreMovies.join(PosterDF,"movie_id").drop("movie_url")
      .orderBy(col("rating_people_num").cast("Int").desc).limit(50)

    // 最近热门电影前50部，添加海报图片地址数据
    val RateMoreRecentlyMoviesPoster = MovieDF.join(PosterDF,"movie_id").drop("movie_url")
      .withColumn("formatted_date", regexp_replace(col("movie_release_date"), "-", ""))
      .orderBy(col("formatted_date").desc).limit(50)
      .drop("formatted_date")

    // 电影平均分前50部，添加海报图片地址数据
    val AverageMoviesPoster = RateMoreMovies.join(PosterDF,"movie_id").drop("movie_url")
      .orderBy(col("movie_rating").cast("Double").desc).limit(50)

    // 如果mongodb中已经有相应的集合先删除
    mongoClient(mongoConfig.db)(READ_RATING_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(RATE_MORE_RECENTLY_MOVIES).dropCollection()
    mongoClient(mongoConfig.db)(AVERAGE_MOVIES).dropCollection()

    // 将结果写入Mongodb
    WriteMongoDB(PosterDF,READ_RATING_COLLECTION)
    WriteMongoDB(PosterDF,RATE_MORE_RECENTLY_MOVIES)
    WriteMongoDB(PosterDF,AVERAGE_MOVIES)

    // 关闭spark应用程序资源连接
    spark.stop()
  }
}
