import MyConfig.{ReadMongoDB, mongoConfig, spark}
import org.apache.spark.mllib.recommendation.{MatrixFactorizationModel, Rating}
import org.apache.spark.rdd.RDD

class ModelTipDM {
  def BestItemTip(): Unit = {
    // 匹配输入参数
    val RATING_TEST = "RatingTest" // 训练集数据(Rating数据集)
    val modelPath = "hdfs://master:9000/input/BaseModel/ItemBest-1" // 推荐模型路径
    val minRatedNumPerUser = 1 //单用户评价电影的最小次数
    val kList = List(10, 20, 30) //推荐数量K值列表
    val resultPath = "hdfs://master:9000/input/ModelTipDM/ItemBestEva-1" // 输出模型评分结果
    // 加载测试数据
    val test = ReadMongoDB(spark, RATING_TEST) // 读取mongodb中的RatingTest数据
    // 加载模型
    val dataModel: RDD[(Int, List[(Int)])] = spark.sparkContext.objectFile[(Int, List[(Int)])](modelPath)
    // RDD[row]转换为RDD[(Int,Int)]
    val dataTest = test.rdd.map(x => (x.getAs[Int]("user_id"), x.getAs[Int]("movie_code")))
    println("测试数据总记录：" + dataTest.count())
    // 过滤测试集记录(user,item)
    val testData = dataTest.groupBy(x => x._1).
      filter(x => x._2.size >= minRatedNumPerUser).flatMap(x => x._2)
    println("测试数据过滤后总记录：" + testData.count())
    val sharedUserIds = testData.keys.distinct.intersection(dataModel.keys.distinct).collect.toList
    println("训练集与测试集中的共有用户：" + sharedUserIds.size)
    val testUserRecords = testData.filter(data => sharedUserIds.contains(data._1))
    val testUserRated = testUserRecords.combineByKey(
      (x: Int) => List(x),
      (c: List[Int], x: Int) => x :: c,
      (c1: List[Int], c2: List[Int]) => c1 ::: c2).cache()
    // 计算不同K值下的召回率，准确率，F1值
    val results = for (k <- kList) yield {
      // RDD[(Int, List[Int])].join(RDD[(Int, List[Int])])，
      // 与测试集比较，获得匹配的推荐记录数(User, TestNum, RecommendNum, MatchedNum)
      val finalResult = testUserRated.join(dataModel.map(x => (x._1, x._2.take(k)))).
        map(x => (x._1, x._2._1.size, x._2._2.size, x._2._1.intersect(x._2._2).size))
      // 正确条数
      val matchedNum = finalResult.map(x => x._4).sum.toInt
      // 召回率，准确率，F1值，Array[(Double, Double, Double)] => Array[(recall, precision, f1)]
      val recall_precision_f1 = finalResult.collect()
        .map(x => {
          val recall = x._4.toDouble / x._2
          val precision = x._4.toDouble / x._3
          (recall, precision)
        })
      // 分别对召回率，准确率，F1值进行累加，, recall_precision_f1.size, matchedNum
      val recall_precision_f1_sum = recall_precision_f1.reduceLeft((x, y) => (x._1 + y._1, x._2 + y._2))
      val avg_recall = (recall_precision_f1_sum._1 * 100) / recall_precision_f1.size
      val avg_precision = (recall_precision_f1_sum._2 * 100) / recall_precision_f1.size
      val F1 = (2 * avg_precision * avg_recall) / (avg_precision + avg_recall)
      (k, avg_recall, avg_precision, F1)
    }
    println(" K,     avg_recall,      avg_precision,          F1")
    results.take(5).foreach(println)
    // 存储结果
    spark.sparkContext.parallelize(results.map(x => x._1 + "," + x._2 + "," + x._3 + "," + x._4))
      .repartition(1).saveAsTextFile(resultPath)
    println("模型评分保存成功！")
  }

  def BestALSTip(): Unit = {
    // 加载数据
    val RATING_TRAIN = "RatingTrain" // 训练集数据(Rating数据集)
    val RATING_TEST = "RatingTest" // 训练集数据(Rating数据集)
    val train = ReadMongoDB(spark, RATING_TRAIN).repartition(2) // 读取mongodb中的RatingTrain数据
    val test = ReadMongoDB(spark, RATING_TEST) // 读取mongodb中的RatingTest数据
    // 匹配输入参数
    val modelPath = "hdfs://master:9000/input/BaseModel/ALSBestModel"
    val minRatedNumPerUser = 2 //单用户评价电影的最小次数
    val kList = "10,20,30".split(",").map(_.toInt) //推荐数量K值列表
    val resultPath = "hdfs://master:9000/input/ModelTipDM/ALS-2"
    // 加载 ALS model
    val model = MatrixFactorizationModel.load(spark.sparkContext, modelPath)
    model.productFeatures.cache
    model.userFeatures.cache
    println("加载模型中...")
    // 加载训练集数据`````````
    val trainData = train.rdd.map(x => (x.getAs[Int]("user_id"),
      x.getAs[Int]("movie_code"), x.getAs[Double]("user_rating")))
    // 加载测试集数据
    val testData = test.rdd.map(x => (x.getAs[Int]("user_id"), x.getAs[Int]("movie_code")))
    val testDataFiltered = testData.groupBy(_._1).
      filter(data => data._2.toList.size >= minRatedNumPerUser).flatMap(_._2).distinct()
    val testUserList = testData.keys.distinct().collect().toList
    val testRecordSet = testData.distinct().collect.toSet
    println("所有测试用户: " + testUserList.size)
    println("所有测试记录: " + testRecordSet.size)

    // 计算训练集与测试集中的共有用户，以及测试集中的相关记录。
    val sharedUserList = trainData.map(_._1).distinct.collect.toList.
      intersect(testDataFiltered.map(_._1).distinct.collect.toList)
    val testSharedSet = testDataFiltered.
      filter(data => sharedUserList.contains(data._1)).collect.toSet
    println("训练集与测试集中的共有用户：" + sharedUserList.size)
    println("测试记录: " + testSharedSet.size)
    // 创建评测结果集的变量
    val evaluationRecords = new scala.collection.mutable.ListBuffer[(Int, Double, Double, Double)]()
    // 评测 model 推荐的结果
    // 计算不同Ｋ值下的 recall,precision,F1
    for (k <- kList) {
      println("================== K=" + k + " ==================")
      var recommendNumSum = 0
      var matchedNumSum = 0
      for (uid <- sharedUserList) {
        val initRecommendRecords = model.recommendProducts(uid, k).map { case Rating(user, item, rating) => (user, item) }
        // 推荐数据集中，匹配测试集中的总数
        val matchedNum = initRecommendRecords.toSet.intersect(testSharedSet).size
        matchedNumSum += matchedNum
      }
      // 推荐K个用户，是指推荐数据集与测试数据集都有的用户
      recommendNumSum = sharedUserList.size * k
      // 计算正确率
      val precision = (matchedNumSum.toDouble / recommendNumSum) * 100
      // 计算召回率
      val recall = (matchedNumSum.toDouble / testSharedSet.size) * 100
      // 对Precision和Recall进行整体评价，F1
      val F1 = (2 * precision * recall) / (precision + recall)
      evaluationRecords += ((k, precision, recall, F1))
      println(s"推荐数量：$k")
      println(s"共有用户(训和测)：${sharedUserList.size}")
      println(s"正确条数：$matchedNumSum")
      println(s"precision：$precision")
      println(s"recall：$recall")
      println(s"F1：$F1")
    }
    val result = spark.sparkContext.parallelize(evaluationRecords).repartition(1)
    result.saveAsTextFile(resultPath)
  }
}

object ModelTipDM {
  def main(args: Array[String]): Unit = {
    //new ModelTipDM().BestItemTip()
    new ModelTipDM().BestALSTip()
  }
}
