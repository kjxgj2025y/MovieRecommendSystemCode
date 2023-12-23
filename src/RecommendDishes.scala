import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.mllib.recommendation.{MatrixFactorizationModel, Rating}
import org.apache.spark.sql.SparkSession

object RecommendDishes {
  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setMaster("local").setAppName("RecommendDishes")
    val sc = new SparkContext(conf)
    val spark = SparkSession.builder().appName("sparkSQL").master("local").getOrCreate()
    val splitter = ","
    // 设置编码数据集的路径
    val userZipCodePath = "data/fagui/userZipCode"
    val mealZipCodePath = "data/fagui/mealZipCode"
    val trainDataPath = "data/fagui/train_data"
    val RecommendItemPath = "data/fagui/RecommendItem"
    // 加载用户编码数据集，网页编码数据集
    val userZipCode = sc.textFile(userZipCodePath).map{
      x=>val fields=x.slice(1,x.size-1).split(splitter); (fields(0),fields(1).toInt)}
    val mealZipCode = sc.textFile(mealZipCodePath).map{
      x=>val fields=x.slice(1,x.size-1).split(splitter); (fields(0),fields(1).toInt)}
    // 对推荐结果集中的用户与网页编码，进行反规约操作，（UserNo->UserID, MealNo->MealID）
    val reverseUserZipCode = userZipCode.map(x=>(x._2,x._1)).collect.toMap
    val reverseMealZipCode = mealZipCode.map(x=>(x._2,x._1)).collect.toMap
    // 代码 4-2加载网页名称的数据集
    // 从外部数据库（MySQL），加载网页数据
    val url = "jdbc:mysql://127.0.0.1:3306/meal_data"
    val mealDF = spark.read.format("jdbc").options(
      Map("url" -> url,
        "user" -> "root",
        "password" -> "123456",
        "dbtable" -> "meal_list")).load()
    // 生成网页数据
    val mealsMap = mealDF.select("mealID","meal_name").rdd.map(row => (row.getString(0),row.getString(1))).collect().toMap
    // 代码 4-3以基于Spark ALS的推荐模型实现单用户网页推荐
    // 加载 ALS model
    val modelPath = "outData/ALS/ALSModel"
    val model = MatrixFactorizationModel.load(sc, modelPath)
    model.productFeatures.cache
    model.userFeatures.cache
    println("model retrieved.")
    // 加载训练集数据
    val trainData = sc.textFile(trainDataPath).map{
      x=>val fields=x.slice(1,x.size-1).split(splitter);
        (fields(0).toInt,fields(1).toInt,fields(2).toDouble)
    }
    // 用户（用户编码=1000）所浏览过的网页编码
    val user1000RateMealIds= trainData.filter(_._1==1000).map(_._2).collect.toSet
    // 用户(用户编码=1000) 未浏览过的网页编码
    val cands1 = trainData.map(x=>x._2).filter(!user1000RateMealIds.contains(_)).distinct
    // 为用户(用户编码=1)推荐10份网页（UserNo,MealNo,Rate）
    val recommendation = model.predict(cands1.map((1000,_))).collect.
      sortBy(-_.rating).take(10)
    val recommendationList = recommendation.map{
      case Rating(user,meal,rating)=>(user,meal,rating)
    }
    // 反编码后的推荐结果集（UserID,MealID,Rate）
    val recommendationRecords = recommendationList.map{
      case (user,meal,rating) => (reverseUserZipCode.get(user).get,
        reverseMealZipCode.get(meal).get,rating)
    }
    // 引用真实的网页名称,最终的推荐结果集（UserID,MealID,Meal,Rate）
    val realRecommendationRecords = recommendationRecords.map{
      case (user,meal,rating) => (user,meal,mealsMap.get(meal).get,rating.toInt)
    }
    // (UserID,MealID,MealName,Rate)
    val result = sc.parallelize(realRecommendationRecords.toSeq).repartition(1)
    result.saveAsTextFile(RecommendItemPath)
  }
}
