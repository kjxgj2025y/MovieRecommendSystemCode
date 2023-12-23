import MyConfig.{ReadMongoDB, WriteMongoDB, mongoClient, mongoConfig, spark}
import org.apache.spark.mllib.recommendation.{MatrixFactorizationModel, Rating}
import org.apache.spark.rdd.RDD

class UseModelRecommender {
  def ItemRecommender(): Unit = {
    val RatingPath = "RatingCode" // 物品编码路径
    val MoviePath = "MovieExpPre" // 电影信息数据集路径
    val TrainPath = "RatingTrain" // 训练集数据路径
    val modelPath = "hdfs://master:9000/input/BaseModel/ItemBest-2"
    val ItemUserRecommender = "ItemRecommenderDF" // ItemBest模型单用户推荐20部电影
    // 加载物品编码数据和电影信息数据
    val reverseMovieZipCode = ReadMongoDB(spark, RatingPath).rdd
      .map(x => (x.getAs[Int]("movie_code"), x.getAs("movie_id").toString)).collect.toMap
    val MovieData = ReadMongoDB(spark, MoviePath).rdd
      .map(x => (x.getAs("movie_id").toString, (x.getAs("movie_name").toString,
        x.getAs[String]("movie_type")))).collect.toMap
    // 加载ItemBest模型
    val dataModel: RDD[(Int, List[(Int)])] = spark.sparkContext.objectFile[(Int, List[(Int)])](modelPath)
    println("ItemBest模型加载成功！")
    // 加载训练集数据
    val trainData = ReadMongoDB(spark, TrainPath).rdd
      .map(x => (
        x.getAs[Int]("user_id"),
        x.getAs[Int]("movie_code")))
    val trainUserRated = trainData.combineByKey(
      (x: Int) => List(x),
      (c: List[Int], x: Int) => x :: c,
      (c1: List[Int], c2: List[Int]) => c1 ::: c2).cache()
    // 过滤训练数据中已有的电影，生成可推荐的新电影集合
    val dataModelNew = dataModel.join(trainUserRated).map(x => (x._1, (x._2._1.diff(x._2._2))))
    // 为用户(用户编码=26500)推荐20部电影（UserNo,MovieNo）
    val recommendation = dataModelNew.map(x => (x._1, x._2.take(20))).
      filter(x => (x._1 == 26500)).flatMap(x => x._2.map(y => (x._1, y)))
    // 反编码后的推荐结果集，并引用真实的电影名称进行推荐(UserID,MovieID,MovieName)
    val recommendationRecords = recommendation.map {
      case (user, movie) => (user, reverseMovieZipCode(movie), MovieData(reverseMovieZipCode(movie)))
    }
    // 保存基于物品推荐结果
    import spark.implicits._
    val RecommenderDF = recommendationRecords.toDF("user_id", "movie_id", "movie_name")
    // 如果mongodb中已经有相应的集合先删除
    mongoClient(mongoConfig.db)(ItemUserRecommender).dropCollection()
   //  将编码数据保存到Mongodb中
    WriteMongoDB(RecommenderDF, ItemUserRecommender)
    RecommenderDF.collect().take(10).foreach(println)
  }

  def ALSRecommender(): Unit = {
    val RatingPath = "RatingCode" // 物品编码路径
    val MoviePath = "MovieExpPre" // 电影信息数据集路径
    val TrainPath = "RatingTrain" // 训练集数据路径
    val ALSUserRecommender = "ALSRecommenderDF" // ALS模型单用户推荐20部电影
    // 加载物品编码数据和电影信息数据
    val reverseMovieZipCode = ReadMongoDB(spark, RatingPath).rdd
      .map(x => (x.getAs[Int]("movie_code"), x.getAs("movie_id").toString)).collect.toMap
    val MovieData = ReadMongoDB(spark, MoviePath).rdd
      .map(x => (x.getAs("movie_id").toString, (x.getAs("movie_name").toString,
        x.getAs[String]("movie_type")))).collect.toMap
    // 加载ALS模型
    val modelPath = "hdfs://master:9000/input/BaseModel/ALSBestModel"
    val model = MatrixFactorizationModel.load(spark.sparkContext, modelPath)
    println("ALS模型加载成功！")
    // 加载训练集数据
    val trainData = ReadMongoDB(spark, TrainPath).rdd
      .map(x => (
        x.getAs[Int]("user_id"),
        x.getAs[Int]("movie_code"),
        x.getAs[Double]("user_rating")))
    // 用户(用户编码=26500)所评价过的电影编码
    val user_eval = trainData.filter(_._1 == 26500).map(_._2).collect.toSet
    // 用户(用户编码=26500)未评价过的电影编码
    val user_uneval = trainData.map(x => x._2).filter(!user_eval.contains(_)).distinct
    // 为用户(用户编码=26500)推荐20部电影(UserNo,MovieNo,Rating)，sortWith函数前后对比条件成立时互换位置
    val recommendation = model.predict(user_uneval.map((26500, _))).collect.sortWith(_.rating > _.rating).take(20)
    val recommendationList = recommendation.map {
      case Rating(user, movie, rating) => (user, movie, rating)
    }
    // 推荐结果集(UserID,MovieID,MovieName,Rating)
    import spark.implicits._
    val recommendationRecords = recommendationList.map {
      case (user, movie, rating) => (user, reverseMovieZipCode(movie), MovieData(reverseMovieZipCode(movie)), rating.toInt.toDouble)
    }
    val RecommenderDF = spark.sparkContext.makeRDD(recommendationRecords)
      .toDF("user_id", "movie_id", "movie_name", "movie_rating")
    RecommenderDF.collect().take(20).foreach(println)
    // 如果mongodb中已经有相应的集合先删除
    mongoClient(mongoConfig.db)(ALSUserRecommender).dropCollection()
    // 将编码数据保存到Mongodb中
    WriteMongoDB(RecommenderDF, ALSUserRecommender)
  }
}

object UseModelRecommender {
  def main(args: Array[String]): Unit = {
    new UseModelRecommender().ItemRecommender()
    new UseModelRecommender().ALSRecommender()
  }
}
