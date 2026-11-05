class SearchHistoryItem {
  const SearchHistoryItem({required this.id,required this.keyword,required this.searchedAt,this.userId});
  final String id; final String? userId; final String keyword; final DateTime searchedAt;
  factory SearchHistoryItem.fromJson(Map<String,dynamic> j)=>SearchHistoryItem(id:j['id'].toString(),userId:j['userId']?.toString(),keyword:j['keyword']?.toString()??'',searchedAt:DateTime.tryParse(j['searchedAt']?.toString()??'')??DateTime.fromMillisecondsSinceEpoch(0));
}
