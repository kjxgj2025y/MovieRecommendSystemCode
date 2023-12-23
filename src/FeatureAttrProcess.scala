import MyConfig.{ReadMongoDB, WriteMongoDB, mongoClient, mongoConfig, spark}
import org.apache.spark.sql.functions.{col, regexp_replace, when}
import org.apache.spark.sql.DataFrame

import java.text.SimpleDateFormat
import java.util.Date


class FeatureAttrProcess {
  //定义MongoDB集合名称
  val RATE_MORE_MOVIES = "RateMoreMovies" // RateMoreMovies(统计历史热门电影)
  val RATE_MORE_RECENTLY_MOVIES = "RateMoreRecentlyMovies" // RateMoreRecentlyMovies(统计最近热门电影)
  val AVERAGE_MOVIES = "AverageMovies" // AverageMovies(统计电影平均得分)
  val GENRES_TOP_MOVIES = "GenresTopMovies" // GenresTopMovies(统计类别优质电影)
  val RATING_CODE = "RatingCode" // RatingCode数据集
  val RATING_TRAIN = "RatingTrain"  // 训练集数据(Rating数据集)
  val RATING_TEST = "RatingTest"  // 测试集数据(Rating数据集)
  val RATING_CHECK = "RatingCheck"  // 验证集数据(Rating数据集)
  import spark.implicits._  // 隐式操作

  // 数据属性规约
  def AttrProcess(Movie: DataFrame): DataFrame = {
    val data_attr_spe = Movie
      // 将movie_writer列中包含"主演:"的数据放到"movie_starring"列里
      .withColumn("movie_starring", when(col("movie_writer").contains("主演"),
        col("movie_writer")).otherwise(col("movie_starring")))
      // 然后将movie_writer列中包含"主演"的数据替换为"未提供"
      .withColumn("movie_writer", when(col("movie_writer").contains("主演"), "未提供").otherwise(col("movie_writer")))
      // "..."或"更多..."替换为"/更多..."，"\\.{3}"表示匹配三个点号
      .withColumn("movie_writer", regexp_replace(col("movie_writer"), "\\.{3}|更多...", "/更多..."))
      .withColumn("movie_starring", regexp_replace(col("movie_starring"), "\\.{3}|更多...", "/更多..."))
    data_attr_spe
  }

  // 统计历史热门电影
  def RateMoreMovies(Rating: DataFrame): Unit = {
    // 创建临时表 rating
    Rating.createOrReplaceTempView("rating")
    // 统计评分最多的电影 mid,count，既是统计历史热门电影
    val rateMoreMoviesDF = spark.sql("select movie_id, count(movie_id) as count from rating " +
      "group by movie_id order by count desc")
    println("统计历史热门电影：")
    rateMoreMoviesDF.show(5)
    // 如果mongodb中已经有相应的集合先删除
    mongoClient(mongoConfig.db)(RATE_MORE_MOVIES).dropCollection()
    // 将结果保存到Mongodb
    WriteMongoDB(rateMoreMoviesDF, RATE_MORE_MOVIES)
  }

  // 统计最近热门电影。按照"yyyyMM"格式选取最近的评分数据，统计评分个数
  def RateMoreRecentlyMovies(Rating: DataFrame): Unit = {
    // 创建一个日期格式化工具
    val simpleDateFormat = new SimpleDateFormat("yyyyMM")
    // 把时间戳转换成年月格式，使用SparkSession.udf.register函数将这个操作进行记录
    spark.udf.register("changeDate", (x: Int) => simpleDateFormat.format(new Date(x * 1000L)).toInt)
    // 创建临时表 rating
    Rating.createOrReplaceTempView("rating")
    // 使用前面定义好的changeDate对rating数据集中的time_stamp列数据做预处理
    val ratingOfYearMonth = spark.sql("select movie_id,user_rating,changeDate(time_stamp) as year_month from rating")
    ratingOfYearMonth.createOrReplaceTempView("rating_month")
    // 从rating_month中查找电影在各个月份的评分，movie_id，count，year_month
    println("统计最近热门电影：")
    val rateMoreRecentlyMoviesDF = spark.sql("select movie_id,count(movie_id) as count," +
      "year_month from rating_month group by year_month,movie_id order by year_month desc,count desc")
    rateMoreRecentlyMoviesDF.show(5)
    // 如果mongodb中已经有相应的集合先删除
    mongoClient(mongoConfig.db)(RATE_MORE_RECENTLY_MOVIES).dropCollection()
    // 将结果保存到Mongodb
    WriteMongoDB(rateMoreRecentlyMoviesDF, RATE_MORE_RECENTLY_MOVIES)
  }

  // 统计电影平均得分，movie_id,avg
  def AverageMovies(Rating: DataFrame): Unit = {
    // 创建临时表 rating使用SQL进行统计
    Rating.createOrReplaceTempView("rating")
    val averageMoviesDF = spark.sql("select movie_id, avg(user_rating) as avg from rating group by movie_id order by avg desc")
    // 如果mongodb中已经有相应的集合先删除
    mongoClient(mongoConfig.db)(AVERAGE_MOVIES).dropCollection()
    // 将结果保存到Mongodb
    WriteMongoDB(averageMoviesDF, AVERAGE_MOVIES)
    println("统计电影平均得分：")
    averageMoviesDF.show(5)
  }

  // 统计类别优质电影
  def GenresTopMovies(Movie: DataFrame, Tag: DataFrame): Unit = {
    // 从Tag数据集中读取所有类别
    val user_tag = Tag.select("user_tag").distinct().filter(col("user_tag").isNotNull)
    val genres = user_tag.rdd.zipWithIndex.filter(x => x._2 != 0).map(x => x._1.toString()
      .replace("[","").replace("]", "")).collect().toList
    // 用inner join把平均评分加入movie表里
    val averageMoviesDF = ReadMongoDB(spark, AVERAGE_MOVIES)
    val movieWithScore = Movie.join(averageMoviesDF, "movie_id").rdd
    // 把genres转成rdd
    val genresRDD = spark.sparkContext.makeRDD(genres)
    // 对类别和电影做笛卡尔积，然后计算电影类别top10
    val genresTopMoviesDF = genresRDD.cartesian(movieWithScore)
    // 条件过滤，找出movie的字段genres值(儿童/动画/科幻)包含当前类别genre(动画...)的那些
      .filter {
        case (movie_type, movieRow) => Option(movieRow.getAs[String]("movie_type")).exists(_.toLowerCase.contains(movie_type.toLowerCase))
      }
      .map {
        case (movie_type, movieRow) => (movie_type, (movieRow.getAs[String]("movie_id").toInt, movieRow.getAs[Double]("avg")))
      }
      .groupByKey()
      .map {
        case (movie_type, items) => (movie_type, items.toList.sortWith(_._2 > _._2).take(10).map(item => (item._1,item._2)))
      }.toDF("movie_type","items")
    // 如果mongodb中已经有相应的集合先删除
    mongoClient(mongoConfig.db)(GENRES_TOP_MOVIES).dropCollection()
    // 将结果保存到Mongodb
    WriteMongoDB(genresTopMoviesDF, GENRES_TOP_MOVIES)
    println("统计类别优质电影：")
    genresTopMoviesDF.show(5)
  }

  // 数据编码
  def DataCodeSplit(Rating: DataFrame): Unit = {
    // 将Rating数据集转换RDD，并指定数据类型
    val dataAll = Rating.rdd.map(x => (
        x.getAs[String]("movie_id").toInt,
        x.getAs[String]("user_id").toInt,
        x.getAs[Double]("user_rating"),
        x.getAs[String]("time_stamp")
      ))
    // 对用户ID和电影ID数据去重后排序
    val umID = dataAll.map(x => (x._1, x._2))
    val userList = umID.map(data => data._1).distinct.sortBy(x => x)
    // 构造用户ID和电影ID编码
    val userZipCode = userList.zipWithIndex.map(data => (data._1, data._2.toInt + 1))
    val userZipCodeMap = userZipCode.collect.toMap
    // 以用户ID和电影ID编码，重新构造Rating数据集
    val UserCode = dataAll.map(x => (userZipCodeMap(x._1), x._2, x._3, x._4, x._1)).sortBy(x => x._3)
      .toDF("movie_code","user_id","user_rating","time_stamp","movie_id")
    UserCode.show(5)
    // 数据分割，有训练数据，测试数据，验证数据
    val Array(train, test, check) = UserCode.randomSplit(Array(0.8, 0.1, 0.1), 123)
    // 如果mongodb中已经有相应的集合先删除
    mongoClient(mongoConfig.db)(RATING_CODE).dropCollection()
    mongoClient(mongoConfig.db)(RATING_TRAIN).dropCollection()
    mongoClient(mongoConfig.db)(RATING_TEST).dropCollection()
    mongoClient(mongoConfig.db)(RATING_CHECK).dropCollection()
    // 将编码数据保存到Mongodb中
    WriteMongoDB(UserCode, RATING_CODE)
    WriteMongoDB(train, RATING_TRAIN)
    WriteMongoDB(test, RATING_TEST)
    WriteMongoDB(check, RATING_CHECK)
    println(mongoClient(mongoConfig.db).collectionNames())
  }
}

object FeatureAttrProcess {
  // MongoDB集合名(读数据)
  val READ_MOVIE_COLLECTION = "MovieExpPre"
  val READ_RATING_COLLECTION = "RatingExpPre"
  val READ_TAG_COLLECTION = "TagExpPre"

  // 构建特征和属性处理
  def main(args: Array[String]): Unit = {
    // 读取mongodb中的MovieExpPre数据(经过数据预处理的)
    val movieDF = ReadMongoDB(spark, READ_MOVIE_COLLECTION)
    // 读取mongodb中的RatingExpPre数据(经过数据预处理的)
    val ratingDF = ReadMongoDB(spark, READ_RATING_COLLECTION)
    // 读取mongodb中的TagExpPre数据(经过数据预处理的)
    val tagDF = ReadMongoDB(spark, READ_TAG_COLLECTION)

    // 调用数据属性规约函数AttrProcess
    val FAP = new FeatureAttrProcess().AttrProcess(movieDF)  // 数据属性规约
    new FeatureAttrProcess().RateMoreMovies(ratingDF)  // 统计历史热门电影
    new FeatureAttrProcess().RateMoreRecentlyMovies(ratingDF)  // 统计最近热门电影
    new FeatureAttrProcess().AverageMovies(ratingDF)  // 统计电影平均得分
    new FeatureAttrProcess().GenresTopMovies(FAP,tagDF)  // 统计类别优质电影
    new FeatureAttrProcess().DataCodeSplit(ratingDF)  // 数据编码
  }
}
