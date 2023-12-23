import MyConfig.{ReadMongoDB, WriteMongoDB, mongoClient, mongoConfig, spark}
import org.apache.spark.mllib.recommendation.{ALS, MatrixFactorizationModel, Rating}
import org.apache.spark.rdd.RDD
import scala.math.min


class BuildModel{
  def ItemModel(): Unit = {
    // 输入参数匹配
    val minRatedNumPerUser = 1  //单用户评价电影的最小次数
    val recommendItemNum = 30  //每个用户推荐物品数
    val RATING_TRAIN = "RatingTrain"  // 训练集数据(Rating数据集)
    // 存储推荐结果集(Model)
    val modelPath = "hdfs://master:9000/input/BaseModel/ItemBest-2"
    // 加载训练数据
    val trainData = ReadMongoDB(spark, RATING_TRAIN).repartition(2)
      .select("user_id","movie_code", "user_rating").rdd
    // 按单用户评价过的最小物品数进行过滤
    val trainDataFiltered = trainData.map{ row => (
      row.getAs[Int]("user_id"),
      row.getAs[Int]("movie_code"),
      row.getAs[Double]("user_rating"))
    }.groupBy(_._1).
      filter(data => data._2.toList.size >= minRatedNumPerUser).
      flatMap(_._2).cache()
    // 转换用户电影评分数据(item,(user,rating))
    val trainUserItemRating = trainData.map(x => (x.getAs[Int]("movie_code"),
      (x.getAs[Int]("user_id"),x.getAs[Double]("user_rating"))))
    // 抽取电影评分数据，取平均分(item, rating(mean))
    val trainItemRating = trainData.map(x => (x.getAs[Int]("user_id"),x.getAs[Double]("user_rating")))
      .groupByKey().map {
      x => (x._1, x._2.sum / x._2.count(x => true))
    }
    // 以用户为键Join数据(user,(item,UserItemRating,ItemRating))
    val itemUserBase = trainUserItemRating.join(trainItemRating).
      map(x => (x._2._1._1, (x._1, x._2._1._2, x._2._2))).cache()
    // [(user, ((itemA,UserItemRating,ItemRating), (itemB,UserItemRating,ItemRating)))]
    val itemMatrix = itemUserBase.join(itemUserBase).filter(f => f._2._1._1 < f._2._2._1)

    // (itemA,itemB),(UserItemARating,ItemARating,UserItemBRating,ItemBRating)
    val itemSimilarityBase = itemMatrix.map(f => ((f._2._1._1, f._2._2._1),
      (f._2._1._2, f._2._1._3, f._2._2._2, f._2._2._3))
    )
    // 计算物品相似度 （应用Jaccard公式）
    val itemSimilarityPre = itemSimilarityBase.map(data => {
      val item1 = data._1._1;
      val item2 = data._1._2;
      val similarity = min(data._2._1, data._2._3) * 1.0 / (data._2._2 + data._2._4)
      ((item1, item2), similarity)
    }).combineByKey(
      x => x,
      (x: Double, y: Double) => x + y,
      (x: Double, y: Double) => x + y
    )
    // 生成物品相似度数据集 item similarity (item,(item,similarity))
    val itemSimilarity = itemSimilarityPre.map(x => ((x._1._2, x._1._1), x._2)).union(itemSimilarityPre).
      map(x => (x._1._1, (x._1._2, x._2)))
    println("物品相似度总行数：" + itemSimilarity.count)
    // 生成推荐模型 (item,List(item))
    val dataModelPre = itemSimilarity.combineByKey(
      (x: (Int, Double)) => List(x),
      (c: List[(Int, Double)], x: (Int, Double)) => c :+ x,
      (c1: List[(Int, Double)], c2: List[(Int, Double)]) => c1 ::: c2)
    // 用推荐模型匹配训练数据，按相似度排序，生成推荐结果集(user,List(item))
    val dataModel = trainDataFiltered.map(x => (x._2, x._1)).join(dataModelPre)
    val recommendModel = dataModel.flatMap(joined => {
      joined._2._2.map(f => (joined._2._1, f._1, f._2))
    }).sortBy(x => (x._1, x._3), ascending = false)
      .map(x => (x._1, x._2))
      .combineByKey(
        (x: Int) => List(x),
        (c: List[Int], x: Int) => c :+ x,
        (c1: List[Int], c2: List[Int]) => c1 ::: c2).
      map(x => (x._1, x._2.take(recommendItemNum)))
    println("基于物品推荐总数：" + recommendModel.count)
    // 存储推荐结果集
    recommendModel.repartition(1).saveAsObjectFile(modelPath)
    println("基于物品推荐结果：")
    recommendModel.take(5).foreach(println)
  }
  def ALSModel(): Unit = {
    val RATING_TRAIN = "RatingTrain"  // 训练集数据(Rating数据集)
    val RATING_CHECK = "RatingCheck"  // 训练集数据(Rating数据集)
    // 隐特征数量
    val listRank = List(10,20,30)
    // 算法迭代次数
    val listIteration = List(10,20)
    // 模型的稀疏性(拟合程度)
    val listLambda = List(0.01,0.1,1.0,1.2)
    // 最优模型参数输出路径
    val ParamOutputPath = "hdfs://master:9000/input/BaseModel/ALSBestParam"
    // 最优模型输出路径
    val ModelOutputPath = "hdfs://master:9000/input/BaseModel/ALSBestModel"

    // 加载训练数据
    val train = ReadMongoDB(spark, RATING_TRAIN).repartition(2)
      .select("user_id","movie_code", "user_rating")
    // 加载验证数据
    val check = ReadMongoDB(spark, RATING_CHECK)
      .select("user_id","movie_code", "user_rating")
    // 将训练集转换RDD
    val trainDataRating = train.rdd.map(x => Rating(x.getAs[Int]("user_id"),
      x.getAs[Int]("movie_code"), x.getAs[Double]("user_rating")))
    // 将验证集转换RDD
    val checkDataRating = check.rdd.map(x => Rating(x.getAs[Int]("user_id"),
      x.getAs[Int]("movie_code"), x.getAs[Double]("user_rating")))

    // 定义计算均方根误差computeRMSE函数，给定的用户ID和电影ID，通过矩阵分解算法预测用户对电影的评分
    def computeRMSE(model: MatrixFactorizationModel, data: RDD[Rating]): Double = {
      val usersProducts = data.map(x => (x.user, x.product))
      val ratingsAndPredictions = data.map { case Rating(user, product, rating) =>
        ((user, product), rating)
      }.join(model.predict(usersProducts).map { case Rating(user, product, rating) =>
        ((user, product), rating)
      }).values;
      math.sqrt(ratingsAndPredictions.map(x => (x._1 - x._2) * (x._1 - x._2)).mean())
    }

    // 初始化最优参数，取极端值
    var bestRMSE = Double.MaxValue
    var bestRank = -10
    var bestIteration = -10
    var bestLambda = -1.0

    // 参数寻优，rank：矩阵分解中的隐特征数量，iter：控制优化算法的迭代次数，lambda：控制模型的稀疏性
    for (rank <- listRank; lambda <- listLambda; iter <- listIteration) {
      val model = ALS.train(trainDataRating, rank, iter, lambda)
      val validationRMSE = computeRMSE(model, checkDataRating)
      if (validationRMSE < bestRMSE) {
        bestRMSE = validationRMSE
        bestRank = rank
        bestLambda = lambda
        bestIteration = iter
      }
    }
    // 输出最优参数组
    println("BestRank:Iteration:BestLambda => BestRMSE")
    println(bestRank + ": " + bestIteration + ": " + bestLambda + " => " + bestRMSE)
    // 输出最优参数
    val result = Array(bestRank + "," + bestIteration + "," + bestLambda + "," + bestRMSE)
    spark.sparkContext.parallelize(result).repartition(1).saveAsTextFile(ParamOutputPath)
    // 输出最优模型
    ALS.train(trainDataRating, bestRank, bestIteration, bestLambda).save(spark.sparkContext, ModelOutputPath)
  }
}

object BuildModel {
  def main(args: Array[String]): Unit = {
    new BuildModel().ItemModel()
    new BuildModel().ALSModel()
  }
}
