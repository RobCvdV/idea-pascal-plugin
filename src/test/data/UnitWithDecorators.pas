unit Next.DriverApi.Rides;

interface

uses
  Next.Core.ResourceAttributes, Next.Core.Promises, Next.Core.IHttpRequest,
  Next.Core.Entity, 
  Next.Core.Struct, 
  Next.Core.TokenType, 
  Next.Core.ResourceResponse,
  System.SysUtils, Next.Core.IGetClaimsFromAuthorizationHeader,
  Next.Core.Permission, Next.DriverApi.Ride, Next.DriverApi.IRideRepository;

type
  [BaseUrl('/rides')]
  TRideResource = class
  private
    FRideRepository: IRideRepository;
    FGetClaimsFromAuthorizationHeader: IGetClaimsFromAuthorizationHeader;
  public
    constructor Create(const ARideRepository: IRideRepository; const AGetClaimsFromAuthorizationHeader: IGetClaimsFromAuthorizationHeader);

    [Authenticate([driver, apitoken])]
    [Path(rmGet, '/')]
    [Summary('Rides')]
    [Description('Get all planned rides for the operator.')]
    [ResultType(TRide)]
    function All(const ARequest: IHttpRequest): IPromise<TEntityList<TRide>>;

    [Authenticate([driver, apitoken])]
    [Path(rmPatch, '/:id')]
    [PathParameter('id', 'string')]
    [Summary('Update ride (start or end)')]
    [Description('Update a ride (start or end).')]
    [RequestBodyType(TRideUpdate)]
    function Update(const ARequest: IHttpRequest): IPromise<TResourceResponse>;
  end;

implementation

uses
  Spring, 
  Next.DriverApi.ResourceHelpers,
  Next.core.Void,
  Next.Core.HttpStatus,
  Next.Core.Id;

{ TRideResource }

constructor TRideResource.Create(
  const ARideRepository: IRideRepository;
  const AGetClaimsFromAuthorizationHeader: IGetClaimsFromAuthorizationHeader);
begin
  Guard.CheckNotNull(AGetClaimsFromAuthorizationHeader, 'AGetClaimsFromAuthorizationHeader');

  FRideRepository := ARideRepository;
  FGetClaimsFromAuthorizationHeader := AGetClaimsFromAuthorizationHeader;
end;

function TRideResource.All(
  const ARequest: IHttpRequest): IPromise<TEntityList<TRide>>;
begin
  var LOperatorId := GetOperatorFromQueryParamsOrAuthHeaderClaim(ARequest, FGetClaimsFromAuthorizationHeader);
  Result := FRideRepository.All(LOperatorId);
end;

function TRideResource.Update(
  const ARequest: IHttpRequest): IPromise<TResourceResponse>;
begin
  var LOperatorId := GetOperatorFromQueryParamsOrAuthHeaderClaim(ARequest, FGetClaimsFromAuthorizationHeader);
  var LRideId := GetIdFromPathParams(ARequest);
  var LMutationIds := GetMutationIdsFromHeader(ARequest);

  Result := GetBodyAsJson(ARequest)
    .&Set('id', LRideId)
    .ToValidatedStruct<TRideUpdate>
    .Op.ThenBy<TVoid>(function(const ARideUpdate: TRideUpdate): IPromise<TVoid> begin
      Result := FRideRepository.Update(LOperatorId, ARideUpdate, LMutationIds);
    end)
    .Op.ToResponseStateAccepted;
end;

end.
