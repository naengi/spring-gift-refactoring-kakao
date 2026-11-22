#### AdminProductManager
- /admin/products/{id}/edit -> 수정 동작인데 POST로 작동
- /admin/products/{id}/delete -> 삭제인데 POST로 작동

#### AdminMemberController
- /admin/members/{id}/edit -> 수정인데 POST로 작동
- /admin/members/{id}/charge-point -> 포인트 충전(PUT)인데 POST로 작동
-  /admin/members/{id}/delete -> 삭제인데 POST로 작동

#### Order
- 주문 할때 Transaction 누락으로 포인트 부족 시 재고만 줄어드는 문제 발생
- 위시리스트 정리 구현 X
- 카카오 메시지가 주문한 본인에게로 감 -> 받는 사람에게 가야함
  - 결정: 받는 사람은 회원 ID(`receiverId`)로 지정한다