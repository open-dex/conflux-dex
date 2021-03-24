package conflux.dex.model;

public class OrderFilter {
	
	public static enum Phase {
		Incompleted((byte) 0),
		Completed((byte) 1);
		
		// database type: TINYINT
		private byte value;
		
		private Phase(byte value) {
			this.value = value;
		}
		
		public byte getValue() {
			return value;
		}
		
		public static Phase parse(OrderStatus status) {
			return status.isCompleted() ? Completed : Incompleted;
		}
		
		public static byte getDelta(OrderStatus oldStatus, OrderStatus newStatus) {
			Phase oldPhase = Phase.parse(oldStatus);
			Phase newPhase = Phase.parse(newStatus);
			
			if (oldPhase == Incompleted && newPhase == Completed) {
				return 1;
			}
			
			return 0;
		}
	}
	
	public static enum SidedPhase {
		BuyIncompleted((byte) 0),
		SellIncompleted((byte) 1),
		BuyCompleted((byte) 2),
		SellCompleted((byte) 3);
		
		// database type: TINYINT
		private byte value;
		
		private SidedPhase(byte value) {
			this.value = value;
		}
		
		public byte getValue() {
			return value;
		}
		
		public static SidedPhase parse(Order order) {
			if (order.getStatus().isCompleted()) {
				return order.getSide() == OrderSide.Buy ? BuyCompleted : SellCompleted;
			} else {
				return order.getSide() == OrderSide.Buy ? BuyIncompleted : SellIncompleted;
			}
		}
		
		public static byte getDelta(OrderStatus oldStatus, OrderStatus newStatus) {
			Phase oldPhase = Phase.parse(oldStatus);
			Phase newPhase = Phase.parse(newStatus);
			
			if (oldPhase == Phase.Incompleted && newPhase == Phase.Completed) {
				return 2;
			}
			
			return 0;
		}
		
		public static SidedPhase parse(OrderSide side, boolean completed) {
			if (OrderSide.Buy.equals(side)) {
				return completed ? BuyCompleted : BuyIncompleted;
			} else {
				return completed ? SellCompleted : SellIncompleted;
			}
		}
	}
	
}
