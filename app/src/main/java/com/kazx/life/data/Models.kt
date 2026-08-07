package com.kazx.life.data

/**
 * 表白墙帖子（列表卡片）。
 */
data class Post(
    val id: String,
    val nickname: String,
    val avatarColor: String,
    val avatarText: String,
    val isAdmin: Boolean,
    val isPinned: Boolean,
    val time: String,
    val content: String,
    val images: List<String>,
    val videos: List<String>,
    val likeCount: Int,
    val commentCount: Int,
    val liked: Boolean,
    val topic: String?
)

/**
 * 帖子详情。
 */
data class PostDetail(
    val post: Post,
    val comments: List<Comment>
)

/**
 * 评论。
 */
data class Comment(
    val id: String,
    val nickname: String,
    val avatarColor: String,
    val avatarText: String,
    val content: String,
    val time: String,
    val isAdmin: Boolean
)

/**
 * 列表分页加载结果。
 */
data class ListPage(
    val posts: List<Post>,
    val loaded: Int,
    val hasMore: Boolean
)

// —— 圈子相关模型 ——

/** 圈子列表项。 */
data class Circle(
    val id: String,
    val name: String,
    val desc: String,
    val icon: String,          // 图片 URL 或空（用占位图）
    val memberCount: Int,
    val postCount: Int
)

/** 圈子详情（头部信息 + 帖子列表）。 */
data class CircleDetail(
    val id: String,
    val name: String,
    val desc: String,
    val icon: String,
    val memberCount: Int,
    val postCount: Int,
    val owner: String,
    val posts: List<CirclePost>
)

/** 圈子里的帖子（列表卡片）。 */
data class CirclePost(
    val id: String,
    val circleId: String,
    val avatarText: String,
    val title: String,
    val summary: String,
    val author: String,
    val time: String,
    val commentCount: Int
)

/** 圈子帖子详情。 */
data class CirclePostDetail(
    val post: CirclePost,
    val content: String,
    val circleName: String,
    val images: List<String>,
    val comments: List<CircleComment>
)

/** 圈子评论。 */
data class CircleComment(
    val id: String,
    val nickname: String,
    val avatarText: String,
    val content: String,
    val time: String
)

// —— 必吃榜相关模型 ——

/** 必吃榜店铺列表项。 */
data class BeachShop(
    val id: String,
    val name: String,
    val cover: String,        // 封面图 URL 或空（占位字）
    val coverText: String,    // 无图时的占位字符
    val rating: Float,        // 0~5 浮点，若未评价则 -1
    val rateCount: Int,       // 评价人数
    val address: String,      // 地址说明
    val claimed: Boolean      // 是否已认领
)

/** 必吃榜店铺详情。 */
data class BeachShopDetail(
    val id: String,
    val name: String,
    val cover: String,
    val coverText: String,
    val rating: Float,
    val rateCount: Int,
    val address: String,
    val phone: String,
    val qq: String,
    val claimed: Boolean,
    val menuImages: List<String>,
    val reviews: List<BeachReview>
)

/** 必吃榜评价。 */
data class BeachReview(
    val id: String,
    val nickname: String,
    val avatarText: String,
    val rating: Float,
    val content: String,
    val time: String
)

/** 通用 API 结果包装。 */
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(data)
    return this
}

inline fun <T> ApiResult<T>.onError(block: (String) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) block(message)
    return this
}
