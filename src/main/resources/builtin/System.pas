unit System;

interface

type
  TClass = class of TObject;

  TObject = class
  public
    { Creates an instance of the class }
    constructor Create;
    { Destroys the object instance }
    destructor Destroy; virtual;
    { Safely destroys the object (checks for nil before calling Destroy) }
    procedure Free;
    { Releases the object (ARC) }
    procedure DisposeOf;
    class function ClassName: string;
    class function ClassParent: TClass;
    class function InheritsFrom(AClass: TClass): Boolean;
    function ClassType: TClass;
    function Equals(Obj: TObject): Boolean; virtual;
    function GetHashCode: Integer; virtual;
    function ToString: string; virtual;
    procedure AfterConstruction; virtual;
    procedure BeforeDestruction; virtual;
  end;

  IInterface = interface
    ['{00000000-0000-0000-C000-000000000046}']
    function QueryInterface(const IID: TGUID; out Obj: IInterface): HResult; stdcall;
    function _AddRef: Integer; stdcall;
    function _Release: Integer; stdcall;
  end;

  IUnknown = IInterface;

implementation

end.
