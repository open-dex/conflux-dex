# DEX Improvement Proposals

## Functionality Enhancements
1. Redirect to [Shuttleflow](https://shuttleflow.io/) for cross-chain deposit or withdrawal, so as to reduce the expensive gas fee for users.
2. Support negative fee rate in order, which allows order maker to earn the trade fee.
3. Support batch settlement for order cancellation on blockchain to avoid too many transactions pended in transaction pool.
4. Prepare necessary tools to recover system 
5. **Instant exchange** related functionalities are obsolete and could be removed.
6. There is a rough [console](../conflux-dex-admin) for administrator to manage the DEX, e.g. query system information or maintain system in manual. You could develop a new one or improve the CSS of current console for better productivity.

## Performance Optimization
1. Currently, the trade settlement throughput on blockchain is about 200 due to block gas limitation. For higher TPS, you could refer to some zkRollup based DEX, e.g. [Loopring](https://github.com/Loopring).
2. To support multiple market makers to place or cancel orders in a very high throughput, we recommend to introduce memory database for incomplete orders. So that, majority orders that never matched and cancelled will not be frequently updated in database.
3. DEX do not provide APIs to query both complete and incomplete orders, so `t_order` table could be splitted into 2 separate tables and remove the index for `status` column. Otherwise, the data size will increase dramatically, and the overhed for index maintanence will become heavior and heavior over time.
4. Could add more caches for both REST APIs and WebSocket APIs, especially for the frequently accessed APIs according to metrics.
5. Settlement of trades on the chain could be batched. It was implemented but disabled (see `TradeSettlement.isBatchable`).

## Scalability & Availability
1. Introduce message queue middleware to replace the current in-memory queue (`Channel<T>` interface), so that service layer could be horizontally extended.
2. Deploy replicated matching engine for order matches to avoid single point failure.
3. Configure multiple Conflux full nodes in private network for blockchain interaction. Note, you have to monitor the health and ensure all full nodes work well.
