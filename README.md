# Conflux DEX
Conflux DEX is a decentralized exchange service built on [Conflux Network](https://confluxnetwork.org/). It provides APIs for users to:

- Deposit/withdraw ERC777 tokens to/from DEX.
- Place, cancel and query orders.
- Query market data.

Please refer to the [technical document](https://open-dex.github.io/conflux-dex-docs/matchflow/) for more details.

## Build Your DEX
If you want to build a DEX on Conflux network, please follow the [deploy document](./scripts/deploy.md) to build a new instance of DEX on Conflux Testnet.

## Development
DEX provides [REST API](https://open-dex.github.io/conflux-dex-docs/matchflow/conflux-dex-api.html) and [WebSocket API](https://open-dex.github.io/conflux-dex-docs/matchflow/ws/) for developers to integrate DEX service with frontend UI pages.

Once REST API changes, you could also run below command under the root folder to generate the latest REST API document, which located at ***build/apiggs/conflux-dex-api.html***.
```
gradle apiggs
```

## DEX Improvement Proposals
As the **first** DAPP on Conflux network, DEX is not aims to work as efficient as a tranditional centralized exchange due to block gas limitation on blockchain. So, there are many important improvements need to be optimized. If you want to build a new DEX, we strongly recommand to read more about the [DIPs](DIP.md).